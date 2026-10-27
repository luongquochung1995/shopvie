package com.shopvi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** P6-4: admin khóa/mở tài khoản, hậu kiểm sản phẩm, khóa shop, banner trang chủ, thống kê tổng quan. */
@IntegrationTest
class AdminModerationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private String admin;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        admin = api.login("admin@shopvi.local");
    }

    @Test
    void lockedUserCannotLoginUntilUnlocked() {
        String email = "it-lock-" + UUID.randomUUID() + "@example.com";
        api.post("/auth/register", null, Map.of("email", email, "password", "secret123", "fullName", "Lock Me"));
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

        JsonNode locked = api.post("/admin/users/" + id + "/lock", admin, Map.of("reason", "Spam")).getBody().at("/data");
        assertThat(locked.at("/status").asText()).isEqualTo("LOCKED");
        assertThat(locked.at("/lockReason").asText()).isEqualTo("Spam");
        assertThat(login(email).getBody().at("/error/code").asText()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, id)).isZero();
        assertThat(api.get("/admin/users?status=LOCKED&q=" + email, admin).getBody().at("/data/items/0/id").asLong())
                .isEqualTo(id);

        api.post("/admin/users/" + id + "/unlock", admin, null);
        assertThat(login(email).getStatusCode()).isEqualTo(HttpStatus.OK);

        long adminId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'admin@shopvi.local'", Long.class);
        assertThat(api.post("/admin/users/" + adminId + "/lock", admin, Map.of("reason", "x")).getBody()
                .at("/error/code").asText()).isEqualTo("CANNOT_LOCK_ADMIN");
        assertThat(api.get("/admin/users", api.registerRandom()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bannedProductIsHiddenAndSellerIsNotified() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.id, p.slug FROM products p JOIN shops s ON s.id = p.shop_id JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND u.email = 'seller3@shopvi.local'
                ORDER BY p.id DESC LIMIT 1""");
        long productId = ((Number) row.get("id")).longValue();
        String slug = (String) row.get("slug");
        String seller = api.login("seller3@shopvi.local");
        try {
            JsonNode banned = api.post("/admin/products/" + productId + "/ban", admin, Map.of("reason", "Hàng giả"))
                    .getBody().at("/data");
            assertThat(banned.at("/status").asText()).isEqualTo("BANNED");
            assertThat(banned.at("/shop/name").asText()).isNotBlank();
            assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(api.get("/seller/products/" + productId, seller).getBody().at("/data/banReason").asText())
                    .isEqualTo("Hàng giả");
            assertThat(api.exchange(HttpMethod.PATCH, "/seller/products/" + productId + "/visibility", seller,
                    Map.of("visible", true), Map.of()).getBody().at("/error/code").asText()).isEqualTo("PRODUCT_BANNED");
            await(() -> "PRODUCT_BANNED".equals(api.get("/notifications", seller).getBody().at("/data/items/0/type").asText()));
            assertThat(api.get("/admin/products?status=BANNED", admin).getBody().at("/data/items")
                    .findValuesAsText("id")).contains(String.valueOf(productId));
        } finally {
            api.post("/admin/products/" + productId + "/unban", admin, null);
        }
        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bannedShopProductsDisappearFromListing() {
        long shopId = jdbc.queryForObject("""
                SELECT s.id FROM shops s JOIN users u ON u.id = s.owner_id WHERE u.email = 'seller2@shopvi.local'""",
                Long.class);
        long before = api.get("/products?size=1", null).getBody().at("/data/totalItems").asLong();
        long ofShop = jdbc.queryForObject("SELECT count(*) FROM products WHERE shop_id = ? AND status = 'ACTIVE' "
                + "AND deleted_at IS NULL", Long.class, shopId);
        try {
            JsonNode banned = api.post("/admin/shops/" + shopId + "/ban", admin, Map.of("reason", "Vi phạm chính sách"))
                    .getBody().at("/data");
            assertThat(banned.at("/status").asText()).isEqualTo("BANNED");
            assertThat(banned.at("/banReason").asText()).isEqualTo("Vi phạm chính sách");
            assertThat(api.get("/products?size=1", null).getBody().at("/data/totalItems").asLong())
                    .isEqualTo(before - ofShop);
        } finally {
            api.post("/admin/shops/" + shopId + "/unban", admin, null);
        }
        assertThat(api.get("/products?size=1", null).getBody().at("/data/totalItems").asLong()).isEqualTo(before);
    }

    @Test
    void bannersShowOnlyActiveOnesInPeriod() {
        Instant now = Instant.now();
        JsonNode live = api.post("/admin/banners", admin, Map.of("title", "IT live", "imageUrl", "https://example.com/a.jpg",
                "linkUrl", "/c/thoi-trang-nam", "position", 1, "active", true)).getBody().at("/data");
        JsonNode future = api.post("/admin/banners", admin, Map.of("title", "IT future", "imageUrl", "https://example.com/b.jpg",
                "position", 2, "active", true, "startsAt", now.plus(1, ChronoUnit.DAYS).toString())).getBody().at("/data");
        try {
            JsonNode list = api.get("/banners", null).getBody().at("/data");
            assertThat(list.findValuesAsText("title")).contains("IT live").doesNotContain("IT future");
            assertThat(api.post("/admin/banners", admin, Map.of("title", "x", "imageUrl", "javascript:alert(1)",
                    "position", 0, "active", true)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            api.delete("/admin/banners/" + live.at("/id").asLong(), admin);
            api.delete("/admin/banners/" + future.at("/id").asLong(), admin);
        }
        assertThat(api.get("/banners", null).getBody().at("/data").findValuesAsText("title")).doesNotContain("IT live");
    }

    @Test
    void overviewCoversEveryDayOfThePeriod() {
        JsonNode stats = api.get("/admin/stats?days=7", admin).getBody().at("/data");
        assertThat(stats.at("/orders/daily")).hasSize(7);
        assertThat(stats.at("/users/total").asLong()).isPositive();
        assertThat(stats.at("/shops/ACTIVE").asLong()).isPositive();
        assertThat(stats.at("/products/ACTIVE").asLong()).isPositive();
        assertThat(api.get("/admin/stats?days=0", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<JsonNode> login(String email) {
        return api.post("/auth/login", null, Map.of("email", email, "password", "secret123"));
    }

    private static void await(Supplier<Boolean> condition) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.get()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 15s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
