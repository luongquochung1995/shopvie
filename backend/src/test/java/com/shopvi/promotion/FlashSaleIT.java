package com.shopvi.promotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.auth.application.JwtTokenService;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P5-5: flash sale — giữ suất trên Redis bằng Lua, tạo đơn bất đồng bộ qua Kafka. DoD: 1000 request đồng thời,
 * quota 100 → đúng 100 đơn, không bán vượt.
 */
@IntegrationTest
class FlashSaleIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtTokenService jwtTokenService;

    private ApiClient api;
    private String admin;
    private long variant;
    private long price;
    private int originalStock;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        admin = api.login("admin@shopvi.local");
        // Phiên còn chạy từ test trước (DB test dùng lại) chiếm chỗ trong /flash-sales/current
        jdbc.update("UPDATE flash_sales SET status = 'CANCELLED' WHERE status = 'ACTIVE'");
        // SKU chưa nằm trong phiên flash sale nào (mỗi test tạo phiên mới cho một SKU khác)
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT v.id, v.price FROM product_variants v JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND s.status = 'ACTIVE' AND v.price >= 10000
                  AND NOT EXISTS (SELECT 1 FROM flash_sale_items i WHERE i.variant_id = v.id)
                ORDER BY v.id LIMIT 1""");
        variant = ((Number) row.get("id")).longValue();
        price = ((Number) row.get("price")).longValue();
        originalStock = stock();
        jdbc.update("UPDATE product_variants SET stock = 500 WHERE id = ?", variant);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", originalStock, variant);
    }

    @Test
    void thousandConcurrentBuyersGetExactlyTheQuota() throws Exception {
        long itemId = createSale(100, 1);
        List<String> tokens = createBuyers(1000);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(100);
        Map<String, Integer> outcomes = new ConcurrentHashMap<>();
        List<String> reservations = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    ResponseEntity<JsonNode> res = api.post("/flash-sales/items/" + itemId + "/purchase", token,
                            Map.of("quantity", 1));
                    String key = res.getStatusCode() == HttpStatus.ACCEPTED ? "ACCEPTED"
                            : res.getBody().at("/error/code").asText();
                    outcomes.merge(key, 1, Integer::sum);
                    if (res.getStatusCode() == HttpStatus.ACCEPTED) {
                        reservations.add(res.getBody().at("/data/reservationId").asText());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(outcomes).containsOnlyKeys("ACCEPTED", "FLASH_SALE_SOLD_OUT");
        assertThat(outcomes.get("ACCEPTED")).isEqualTo(100);
        assertThat(outcomes.get("FLASH_SALE_SOLD_OUT")).isEqualTo(900);

        // Đơn được tạo bất đồng bộ (Kafka) — chờ đủ 100 lượt CONFIRMED
        await(() -> count("SELECT count(*) FROM flash_sale_reservations WHERE item_id = ? AND status = 'CONFIRMED'",
                itemId) == 100, 90);
        assertThat(count("SELECT sold FROM flash_sale_items WHERE id = ?", itemId)).isEqualTo(100);
        assertThat(stock()).isEqualTo(400);
        assertThat(count("""
                SELECT count(*) FROM orders o JOIN flash_sale_reservations r ON r.id = o.checkout_id
                WHERE r.item_id = ? AND o.payment_method = 'COD'""", itemId)).isEqualTo(100);
        assertThat(count("""
                SELECT count(*) FROM order_items oi JOIN orders o ON o.id = oi.order_id
                JOIN flash_sale_reservations r ON r.id = o.checkout_id
                WHERE r.item_id = ? AND oi.unit_price = ?""", itemId, price / 2)).isEqualTo(100);
        JsonNode item = currentItem(itemId);
        assertThat(item.at("/remaining").asInt()).isZero();
        assertThat(reservations).hasSize(100);
    }

    @Test
    void reservationTurnsIntoOrderAndPerUserLimitApplies() {
        long itemId = createSale(10, 1);
        String buyer = createBuyers(1).getFirst();

        var accepted = api.post("/flash-sales/items/" + itemId + "/purchase", buyer, Map.of("quantity", 1));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String reservationId = accepted.getBody().at("/data/reservationId").asText();
        assertThat(accepted.getBody().at("/data/status").asText()).isEqualTo("PENDING");

        var again = api.post("/flash-sales/items/" + itemId + "/purchase", buyer, Map.of("quantity", 1));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(again.getBody().at("/error/code").asText()).isEqualTo("FLASH_SALE_LIMIT_REACHED");

        JsonNode confirmed = awaitReservation(buyer, reservationId, "CONFIRMED");
        String orderCode = confirmed.at("/orderCode").asText();
        JsonNode order = api.get("/orders/" + orderCode, buyer).getBody().at("/data");
        assertThat(order.at("/paymentMethod").asText()).isEqualTo("COD");
        assertThat(order.at("/items/0/unitPrice").asLong()).isEqualTo(price / 2);
        assertThat(order.at("/subtotal").asLong()).isEqualTo(price / 2);

        // Lượt giữ của người khác không xem được
        assertThat(api.get("/flash-sales/reservations/" + reservationId, createBuyers(1).getFirst()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(currentItem(itemId).at("/remaining").asInt()).isEqualTo(9);
    }

    @Test
    void failedOrderReturnsTheSlot() {
        long itemId = createSale(5, 2);
        String buyer = createBuyers(1).getFirst();
        jdbc.update("UPDATE product_variants SET stock = 0 WHERE id = ?", variant);

        String reservationId = api.post("/flash-sales/items/" + itemId + "/purchase", buyer, Map.of("quantity", 2))
                .getBody().at("/data/reservationId").asText();
        JsonNode failed = awaitReservation(buyer, reservationId, "FAILED");
        assertThat(failed.at("/reason").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(currentItem(itemId).at("/remaining").asInt()).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM orders WHERE checkout_id = ?::uuid", reservationId)).isZero();

        // Suất đã trả → người đó mua lại được khi có hàng
        jdbc.update("UPDATE product_variants SET stock = 10 WHERE id = ?", variant);
        String retry = api.post("/flash-sales/items/" + itemId + "/purchase", buyer, Map.of("quantity", 2))
                .getBody().at("/data/reservationId").asText();
        awaitReservation(buyer, retry, "CONFIRMED");
    }

    @Test
    void adminValidatesAndCancelsSales() {
        Instant now = Instant.now();
        var invalid = api.post("/admin/flash-sales", admin, Map.of("name", "Sai giá",
                "startAt", now.toString(), "endAt", now.plus(1, ChronoUnit.HOURS).toString(),
                "items", List.of(Map.of("variantId", variant, "salePrice", price, "quota", 5, "perUserLimit", 1))));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().at("/error/details/0/field").asText()).isEqualTo("items[0].salePrice");

        String buyer = createBuyers(1).getFirst();
        assertThat(api.post("/admin/flash-sales", buyer, Map.of()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long itemId = createSale(5, 1);
        long saleId = count("SELECT flash_sale_id FROM flash_sale_items WHERE id = ?", itemId);
        assertThat(api.post("/admin/flash-sales/" + saleId + "/cancel", admin, null).getBody().at("/data/status")
                .asText()).isEqualTo("CANCELLED");
        var closed = api.post("/flash-sales/items/" + itemId + "/purchase", buyer, Map.of("quantity", 1));
        assertThat(closed.getBody().at("/error/code").asText()).isEqualTo("FLASH_SALE_NOT_ACTIVE");
    }

    // ----- helpers

    /** Phiên đang chạy với một SKU giá bằng nửa giá gốc; trả id item. */
    private long createSale(int quota, int perUserLimit) {
        Instant now = Instant.now();
        var res = api.post("/admin/flash-sales", admin, Map.of("name", "IT flash sale",
                "startAt", now.minus(1, ChronoUnit.MINUTES).toString(),
                "endAt", now.plus(1, ChronoUnit.HOURS).toString(),
                "items", List.of(Map.of("variantId", variant, "salePrice", price / 2, "quota", quota,
                        "perUserLimit", perUserLimit))));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().at("/data/running").asBoolean()).isTrue();
        return res.getBody().at("/data/items/0/id").asLong();
    }

    /** Tạo nhanh người mua (kèm địa chỉ mặc định) bằng JDBC và cấp access token. */
    private List<String> createBuyers(int n) {
        String batch = UUID.randomUUID().toString().substring(0, 8);
        List<String> tokens = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String email = "it-fs-" + batch + "-" + i + "@example.com";
            Long id = jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name) VALUES (?, 'x', 'FS') "
                    + "RETURNING id", Long.class, email);
            jdbc.update("INSERT INTO addresses (user_id, receiver_name, phone, province_code, ward_code, detail, "
                    + "is_default) VALUES (?, 'Người nhận', '0901234567', '79', '26740', '1 Lê Lợi', TRUE)", id);
            tokens.add(jwtTokenService.issue(new UserAccount(id, email, "FS", UserRole.BUYER, UserStatus.ACTIVE))
                    .value());
        }
        return tokens;
    }

    private JsonNode currentItem(long itemId) {
        for (JsonNode sale : api.get("/flash-sales/current", null).getBody().at("/data")) {
            for (JsonNode item : sale.at("/items")) {
                if (item.at("/id").asLong() == itemId) {
                    return item;
                }
            }
        }
        throw new AssertionError("item " + itemId + " not in current flash sales");
    }

    private JsonNode awaitReservation(String token, String id, String status) {
        JsonNode[] last = new JsonNode[1];
        await(() -> {
            last[0] = api.get("/flash-sales/reservations/" + id, token).getBody().at("/data");
            return status.equals(last[0].at("/status").asText());
        }, 30);
        return last[0];
    }

    private static void await(Supplier<Boolean> condition, int seconds) {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        while (!condition.get()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + seconds + "s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variant);
    }
}
