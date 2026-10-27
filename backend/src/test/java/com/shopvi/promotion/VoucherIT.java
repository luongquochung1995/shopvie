package com.shopvi.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/** P5-4: voucher sàn / voucher shop khi thanh toán, giới hạn lượt dùng nguyên tử, hoàn lượt khi hủy đơn. */
@IntegrationTest
class VoucherIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private String admin;
    private long variant;
    private long shopId;
    private int originalStock;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        admin = api.login("admin@shopvi.local");
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT v.id, p.shop_id FROM product_variants v JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND s.status = 'ACTIVE'
                  AND u.email = 'seller1@shopvi.local' AND v.price >= 50000
                ORDER BY v.id LIMIT 1""");
        variant = ((Number) row.get("id")).longValue();
        shopId = ((Number) row.get("shop_id")).longValue();
        originalStock = jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variant);
        jdbc.update("UPDATE product_variants SET stock = 100 WHERE id = ?", variant);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", originalStock, variant);
    }

    @Test
    void platformVoucherDiscountsCheckoutAndIsReturnedOnCancel() {
        String code = createVoucher(admin, "/admin/vouchers", "FIXED", 20000, 5, 1);
        String buyer = api.registerRandom();
        long addressId = buyerWithCart(buyer);

        JsonNode preview = api.post("/checkout/preview", buyer, Map.of("voucherCodes", List.of(code.toLowerCase())))
                .getBody().at("/data");
        assertThat(preview.at("/discount").asLong()).isEqualTo(20000);
        assertThat(preview.at("/total").asLong())
                .isEqualTo(preview.at("/subtotal").asLong() + preview.at("/shippingFee").asLong() - 20000);
        assertThat(preview.at("/voucherCodes/0").asText()).isEqualTo(code);

        var placed = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "COD",
                "expectedTotal", preview.at("/total").asLong(), "voucherCodes", List.of(code)));
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderCode = placed.getBody().at("/data/orders/0/code").asText();
        JsonNode order = api.get("/orders/" + orderCode, buyer).getBody().at("/data");
        assertThat(order.at("/discount").asLong()).isEqualTo(20000);
        assertThat(order.at("/vouchers/0/code").asText()).isEqualTo(code);
        assertThat(usedCount(code)).isEqualTo(1);

        // Mỗi người dùng 1 lần
        buyerWithCart(buyer);
        var reuse = api.post("/checkout/preview", buyer, Map.of("voucherCodes", List.of(code)));
        assertThat(reuse.getBody().at("/error/code").asText()).isEqualTo("VOUCHER_NOT_APPLICABLE");

        // Hủy đơn → hoàn lượt dùng, dùng lại được
        api.post("/orders/" + orderCode + "/cancel", buyer, Map.of("reason", "Đổi ý"));
        assertThat(usedCount(code)).isZero();
        assertThat(api.post("/checkout/preview", buyer, Map.of("voucherCodes", List.of(code))).getBody()
                .at("/data/discount").asLong()).isEqualTo(20000);

        assertThat(api.post("/checkout/preview", buyer, Map.of("voucherCodes", List.of("NOPE1234"))).getBody()
                .at("/error/code").asText()).isEqualTo("VOUCHER_NOT_FOUND");
    }

    @Test
    void shopVoucherAppliesOnlyToItsShopAndShowsInAvailableList() {
        String seller = api.login("seller1@shopvi.local");
        String code = createVoucher(seller, "/seller/vouchers", "PERCENT", 10, 10, 1);
        String buyer = api.registerRandom();
        buyerWithCart(buyer);

        JsonNode available = api.get("/vouchers/available?shopIds=" + shopId, buyer).getBody().at("/data");
        assertThat(available.findValuesAsText("code")).contains(code);

        JsonNode preview = api.post("/checkout/preview", buyer, Map.of("voucherCodes", List.of(code))).getBody()
                .at("/data");
        long subtotal = preview.at("/groups/0/subtotal").asLong();
        assertThat(preview.at("/groups/0/discount").asLong()).isEqualTo(subtotal / 10);

        // Người khác không ngừng được voucher của shop
        long id = jdbc.queryForObject("SELECT id FROM vouchers WHERE code = ?", Long.class, code);
        assertThat(api.post("/seller/vouchers/" + id + "/disable", api.login("seller2@shopvi.local"), null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Voucher còn 3 lượt, 10 người đặt hàng cùng lúc → đúng 3 đơn dùng được voucher. */
    @Test
    void concurrentRedemptionsNeverExceedUsageLimit() throws Exception {
        String code = createVoucher(admin, "/admin/vouchers", "FIXED", 10000, 3, 1);
        List<String> buyers = new ArrayList<>();
        Map<String, Long> addresses = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            String buyer = api.registerRandom();
            buyers.add(buyer);
            addresses.put(buyer, buyerWithCart(buyer));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<ResponseEntity<JsonNode>> results = new ArrayList<>();
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (String buyer : buyers) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return api.post("/checkout", buyer, Map.of("addressId", addresses.get(buyer),
                            "paymentMethod", "COD", "voucherCodes", List.of(code)));
                }));
            }
            start.countDown();
            for (Future<ResponseEntity<JsonNode>> f : futures) {
                results.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }

        long created = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        assertThat(created).isEqualTo(3);
        assertThat(results.stream().filter(r -> r.getStatusCode() != HttpStatus.CREATED)
                .map(r -> r.getBody().at("/error/code").asText()))
                .allMatch(c -> c.equals("VOUCHER_EXHAUSTED") || c.equals("VOUCHER_NOT_APPLICABLE"));
        assertThat(usedCount(code)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM voucher_usages u JOIN vouchers v ON v.id = u.voucher_id WHERE v.code = ?""",
                Long.class, code)).isEqualTo(3);
    }

    // ----- helpers

    private String createVoucher(String token, String path, String type, long value, int usageLimit, int perUser) {
        String code = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Instant now = Instant.now();
        var res = api.post(path, token, Map.of("code", code, "name", "Voucher IT", "type", type, "value", value,
                "minOrderValue", 0, "usageLimit", usageLimit, "perUserLimit", perUser,
                "startAt", now.minus(1, ChronoUnit.MINUTES).toString(),
                "endAt", now.plus(1, ChronoUnit.DAYS).toString()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    /** Thêm địa chỉ (lần đầu) + một món của shop seller1 vào giỏ; trả id địa chỉ mặc định. */
    private long buyerWithCart(String token) {
        JsonNode list = api.get("/me/addresses", token).getBody().at("/data");
        long addressId = list.isArray() && !list.isEmpty() ? list.get(0).at("/id").asLong()
                : api.post("/me/addresses", token, Map.of("receiverName", "Người nhận", "phone", "0901234567",
                        "provinceCode", "79", "wardCode", "26740", "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
        assertThat(api.post("/cart/items", token, Map.of("variantId", variant, "quantity", 1)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        return addressId;
    }

    private long usedCount(String code) {
        return jdbc.queryForObject("SELECT used_count FROM vouchers WHERE code = ?", Long.class, code);
    }
}
