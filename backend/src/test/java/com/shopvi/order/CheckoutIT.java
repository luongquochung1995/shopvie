package com.shopvi.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CheckoutIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    /** stock gốc của các SKU bị test sửa, để khôi phục sau mỗi test. */
    private final Map<Long, Integer> originalStock = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @AfterEach
    void restoreStock() {
        originalStock.forEach((id, stock) -> jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", stock, id));
    }

    @Test
    void cartGroupsByShopAndValidatesStock() {
        String token = api.registerRandom();
        long[] variants = variantsOfDifferentShops();
        setStock(variants[0], 5);
        setStock(variants[1], 5);

        assertThat(api.post("/cart/items", token, Map.of("variantId", variants[0], "quantity", 2)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        api.post("/cart/items", token, Map.of("variantId", variants[0], "quantity", 1));
        JsonNode cart = api.post("/cart/items", token, Map.of("variantId", variants[1], "quantity", 1)).getBody().at("/data");

        assertThat(cart.at("/itemCount").asInt()).isEqualTo(2);
        assertThat(cart.at("/shops")).hasSize(2);
        assertThat(findItem(cart, variants[0]).at("/quantity").asInt()).isEqualTo(3);
        assertThat(api.get("/cart/count", token).getBody().at("/data/count").asLong()).isEqualTo(2);

        // Vượt tồn kho: chỉ còn thêm được 2
        var tooMany = api.post("/cart/items", token, Map.of("variantId", variants[0], "quantity", 3));
        assertThat(tooMany.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooMany.getBody().at("/error/code").asText()).isEqualTo("CART_QUANTITY_EXCEEDS_STOCK");
        assertThat(tooMany.getBody().at("/error/details/0/message").asText()).isEqualTo("2");

        long itemId = findItem(cart, variants[0]).at("/id").asLong();
        var unselected = api.exchange(org.springframework.http.HttpMethod.PATCH, "/cart/items/" + itemId, token,
                Map.of("selected", false), Map.of()).getBody().at("/data");
        assertThat(findItem(unselected, variants[0]).at("/selected").asBoolean()).isFalse();
        assertThat(unselected.at("/selectedQuantity").asInt()).isEqualTo(1);

        // Người khác không sửa được dòng của mình
        String stranger = api.registerRandom();
        assertThat(api.exchange(org.springframework.http.HttpMethod.PATCH, "/cart/items/" + itemId, stranger,
                Map.of("quantity", 1), Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void checkoutSplitsOrdersByShopDecrementsStockAndClearsPurchasedItems() {
        String token = api.registerRandom();
        long addressId = createAddress(token);
        long[] variants = variantsOfDifferentShops();
        setStock(variants[0], 10);
        setStock(variants[1], 10);
        api.post("/cart/items", token, Map.of("variantId", variants[0], "quantity", 2));
        api.post("/cart/items", token, Map.of("variantId", variants[1], "quantity", 1));

        JsonNode preview = api.post("/checkout/preview", token, Map.of()).getBody().at("/data");
        assertThat(preview.at("/groups")).hasSize(2);
        assertThat(preview.at("/address/id").asLong()).isEqualTo(addressId);
        // Phí ship tính theo địa chỉ lấy hàng của từng shop (xem PaymentShippingIT), tổng = cộng các shop
        assertThat(preview.at("/shippingFee").asLong()).isPositive()
                .isEqualTo(preview.at("/groups/0/shippingFee").asLong() + preview.at("/groups/1/shippingFee").asLong());
        long total = preview.at("/total").asLong();

        var placed = api.post("/checkout", token, Map.of("addressId", addressId, "paymentMethod", "COD",
                "expectedTotal", total, "notes", Map.of(preview.at("/groups/0/shop/id").asText(), "Giao giờ hành chính")));
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode result = placed.getBody().at("/data");
        assertThat(result.at("/orders")).hasSize(2);
        assertThat(result.at("/total").asLong()).isEqualTo(total);

        assertThat(stock(variants[0])).isEqualTo(8);
        assertThat(stock(variants[1])).isEqualTo(9);
        assertThat(api.get("/cart", token).getBody().at("/data/itemCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT checkout_id) FROM orders WHERE code IN (?, ?)", Long.class,
                result.at("/orders/0/code").asText(), result.at("/orders/1/code").asText())).isEqualTo(1);

        // Danh sách & chi tiết đơn
        JsonNode list = api.get("/orders?status=PENDING", token).getBody().at("/data");
        assertThat(list.at("/totalItems").asLong()).isEqualTo(2);
        String code = result.at("/orders/0/code").asText();
        JsonNode detail = api.get("/orders/" + code, token).getBody().at("/data");
        assertThat(detail.at("/status").asText()).isEqualTo("PENDING");
        assertThat(detail.at("/cancellable").asBoolean()).isTrue();
        assertThat(detail.at("/shippingAddress/wardCode").asText()).isEqualTo("26740");
        assertThat(detail.at("/history/0/toStatus").asText()).isEqualTo("PENDING");
        assertThat(detail.at("/items/0/productName").asText()).isNotBlank();
        assertThat(api.get("/orders/" + code, api.registerRandom()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelRestoresStockAndCannotBeRepeated() {
        String token = api.registerRandom();
        long addressId = createAddress(token);
        long variant = variantsOfDifferentShops()[0];
        setStock(variant, 5);
        api.post("/cart/items", token, Map.of("variantId", variant, "quantity", 3));
        String code = api.post("/checkout", token, Map.of("addressId", addressId, "paymentMethod", "COD"))
                .getBody().at("/data/orders/0/code").asText();
        assertThat(stock(variant)).isEqualTo(2);

        JsonNode cancelled = api.post("/orders/" + code + "/cancel", token, Map.of("reason", "Đổi ý")).getBody().at("/data");
        assertThat(cancelled.at("/status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.at("/history/1/note").asText()).isEqualTo("Đổi ý");
        assertThat(stock(variant)).isEqualTo(5);

        var again = api.post("/orders/" + code + "/cancel", token, null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().at("/error/code").asText()).isEqualTo("INVALID_ORDER_STATUS_TRANSITION");
        assertThat(stock(variant)).isEqualTo(5);
    }

    @Test
    void priceChangeAndEmptySelectionAreRejected() {
        String token = api.registerRandom();
        long addressId = createAddress(token);
        assertThat(api.post("/checkout/preview", token, null).getBody().at("/error/code").asText())
                .isEqualTo("NO_ITEMS_SELECTED");

        long variant = variantsOfDifferentShops()[0];
        setStock(variant, 5);
        api.post("/cart/items", token, Map.of("variantId", variant, "quantity", 1));
        var changed = api.post("/checkout", token, Map.of("addressId", addressId, "paymentMethod", "COD",
                "expectedTotal", 1));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody().at("/error/code").asText()).isEqualTo("PRICE_CHANGED");
        assertThat(stock(variant)).isEqualTo(5);
        assertThat(api.get("/cart", token).getBody().at("/data/itemCount").asInt()).isEqualTo(1);
    }

    /** P2-6: hai người cùng mua món cuối cùng cùng lúc → chỉ một người thành công, kho không âm. */
    @Test
    void concurrentCheckoutForLastItemSellsExactlyOnce() throws Exception {
        long variant = variantsOfDifferentShops()[0];
        setStock(variant, 1);
        List<String> tokens = new ArrayList<>();
        List<Long> addresses = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String token = api.registerRandom();
            tokens.add(token);
            addresses.add(createAddress(token));
            assertThat(api.post("/cart/items", token, Map.of("variantId", variant, "quantity", 1)).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String token = tokens.get(i);
                long addressId = addresses.get(i);
                Callable<ResponseEntity<JsonNode>> task = () -> {
                    start.await();
                    return api.post("/checkout", token, Map.of("addressId", addressId, "paymentMethod", "COD"));
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> f : futures) {
                ResponseEntity<JsonNode> res = f.get();
                statuses.add(HttpStatus.valueOf(res.getStatusCode().value()));
                if (res.getStatusCode() == HttpStatus.CONFLICT) {
                    assertThat(res.getBody().at("/error/code").asText()).isEqualTo("OUT_OF_STOCK");
                }
            }
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stock(variant)).isZero();
        long ordersCreated = tokens.stream()
                .mapToLong(t -> api.get("/orders", t).getBody().at("/data/totalItems").asLong())
                .sum();
        assertThat(ordersCreated).isEqualTo(1);
    }

    // ----- helpers

    /** Hai SKU của hai shop khác nhau (sản phẩm đang bán). */
    private long[] variantsOfDifferentShops() {
        List<Long> ids = jdbc.queryForList("""
                SELECT DISTINCT ON (p.shop_id) v.id FROM product_variants v JOIN products p ON p.id = v.product_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL ORDER BY p.shop_id, v.id DESC""", Long.class);
        return new long[]{ids.get(0), ids.get(1)};
    }

    private void setStock(long variantId, int stock) {
        originalStock.putIfAbsent(variantId, stock(variantId));
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", stock, variantId);
    }

    private int stock(long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private long createAddress(String token) {
        return api.post("/me/addresses", token, Map.of("receiverName", "Người nhận", "phone", "0901234567",
                "provinceCode", "79", "wardCode", "26740", "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
    }

    private static JsonNode findItem(JsonNode cart, long variantId) {
        for (JsonNode shop : cart.at("/shops")) {
            for (JsonNode item : shop.at("/items")) {
                if (item.at("/variantId").asLong() == variantId) {
                    return item;
                }
            }
        }
        throw new AssertionError("variant " + variantId + " not in cart");
    }
}
