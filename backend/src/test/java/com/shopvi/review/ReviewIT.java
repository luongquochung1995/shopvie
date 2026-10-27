package com.shopvi.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** P5-3: đánh giá sau khi đơn hoàn thành — 1 lần/món, cập nhật điểm sản phẩm + shop, seller phản hồi. */
@IntegrationTest
class ReviewIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;
    private long variant;
    private long productId;
    private String slug;
    private int originalStock;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        // Sản phẩm của seller1 chưa có đánh giá nào (điểm tính lại từ đầu)
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT v.id, p.id AS product_id, p.slug FROM product_variants v JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND u.email = 'seller1@shopvi.local'
                  AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.product_id = p.id)
                ORDER BY v.id LIMIT 1""");
        variant = ((Number) row.get("id")).longValue();
        productId = ((Number) row.get("product_id")).longValue();
        slug = (String) row.get("slug");
        originalStock = jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variant);
        jdbc.update("UPDATE product_variants SET stock = 50 WHERE id = ?", variant);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", originalStock, variant);
    }

    @Test
    void buyerReviewsCompletedOrderOnceAndSellerReplies() {
        String buyer = api.registerRandom();
        String code = placeOrder(buyer);
        JsonNode order = api.get("/orders/" + code, buyer).getBody().at("/data");
        long itemId = order.at("/items/0/id").asLong();

        // Chưa hoàn thành → chưa đánh giá được
        var early = api.post("/orders/" + code + "/items/" + itemId + "/review", buyer, Map.of("rating", 5));
        assertThat(early.getBody().at("/error/code").asText()).isEqualTo("REVIEW_NOT_ALLOWED");

        jdbc.update("UPDATE orders SET status = 'DELIVERED' WHERE code = ?", code);
        assertThat(api.post("/orders/" + code + "/complete", buyer, null).getBody().at("/data/status").asText())
                .isEqualTo("COMPLETED");

        var created = api.post("/orders/" + code + "/items/" + itemId + "/review", buyer, Map.of("rating", 4,
                "content", "Hàng đẹp, giao nhanh", "media", List.of("https://example.com/a.jpg")));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long reviewId = created.getBody().at("/data/id").asLong();
        assertThat(created.getBody().at("/data/reviewerName").asText()).contains("*");

        var duplicate = api.post("/orders/" + code + "/items/" + itemId + "/review", buyer, Map.of("rating", 1));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().at("/error/code").asText()).isEqualTo("REVIEW_ALREADY_EXISTS");
        // Người khác không đánh giá hộ được
        assertThat(api.post("/orders/" + code + "/items/" + itemId + "/review", api.registerRandom(),
                Map.of("rating", 5)).getStatusCode()).isNotEqualTo(HttpStatus.CREATED);

        // Đơn thứ hai 2 sao → trung bình 3.0
        String code2 = placeOrder(buyer);
        jdbc.update("UPDATE orders SET status = 'COMPLETED' WHERE code = ?", code2);
        long itemId2 = api.get("/orders/" + code2, buyer).getBody().at("/data/items/0/id").asLong();
        api.post("/orders/" + code2 + "/items/" + itemId2 + "/review", buyer, Map.of("rating", 2));

        JsonNode reviews = api.get("/products/" + slug + "/reviews", null).getBody().at("/data");
        assertThat(reviews.at("/summary/ratingCount").asLong()).isEqualTo(2);
        assertThat(reviews.at("/summary/ratingAvg").decimalValue()).isEqualByComparingTo("3.0");
        assertThat(reviews.at("/summary/withMedia").asLong()).isEqualTo(1);
        assertThat(api.get("/products/" + slug + "/reviews?rating=4", null).getBody().at("/data/totalItems").asLong())
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating_count FROM products WHERE id = ?", Integer.class, productId))
                .isEqualTo(2);
        assertThat(api.get("/orders/" + code + "/reviews", buyer).getBody().at("/data")).hasSize(1);

        // Seller phản hồi 1 lần
        String seller = api.login("seller1@shopvi.local");
        assertThat(api.get("/seller/reviews?unreplied=true", seller).getBody().at("/data/items")
                .findValuesAsText("id")).contains(String.valueOf(reviewId));
        var reply = api.post("/seller/reviews/" + reviewId + "/reply", seller, Map.of("content", "Cảm ơn bạn!"));
        assertThat(reply.getBody().at("/data/sellerReply").asText()).isEqualTo("Cảm ơn bạn!");
        assertThat(api.post("/seller/reviews/" + reviewId + "/reply", seller, Map.of("content", "Lần 2"))
                .getBody().at("/error/code").asText()).isEqualTo("REVIEW_ALREADY_REPLIED");
        assertThat(api.post("/seller/reviews/" + reviewId + "/reply", api.login("seller2@shopvi.local"),
                Map.of("content", "Không phải shop tôi")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String placeOrder(String buyer) {
        JsonNode addresses = api.get("/me/addresses", buyer).getBody().at("/data");
        long addressId = !addresses.isEmpty() ? addresses.get(0).at("/id").asLong()
                : api.post("/me/addresses", buyer, Map.of("receiverName", "Nguyễn Văn An", "phone", "0901234567",
                        "provinceCode", "79", "wardCode", "26740", "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 1));
        var placed = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "COD"));
        assertThat(placed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return placed.getBody().at("/data/orders/0/code").asText();
    }
}
