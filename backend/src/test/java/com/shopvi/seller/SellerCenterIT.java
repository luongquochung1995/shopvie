package com.shopvi.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seller Center (DoD Phase 3): mở shop → admin duyệt → seller đăng sản phẩm có phân loại → sản phẩm hiển thị cho
 * người mua → người mua đặt hàng → seller xử lý đơn tới DELIVERED → dashboard có doanh số.
 */
@IntegrationTest
class SellerCenterIT {

    private static final String PASSWORD = "secret123";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void sellerOpensShopListsProductAndFulfilsOrderToDelivered() {
        // 1. Người mua mở shop → shop PENDING, tài khoản thành SELLER (token cũ vẫn mang role BUYER)
        String email = randomEmail();
        String buyerToken = register(email);
        var registered = api.post("/seller/shop/register", buyerToken,
                Map.of("name", "Shop IT " + UUID.randomUUID().toString().substring(0, 8), "description", "Shop test"));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long shopId = registered.getBody().at("/data/id").asLong();
        assertThat(registered.getBody().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(api.get("/seller/shop", buyerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorCode(api.post("/seller/shop/register", buyerToken, Map.of("name", "Shop thứ hai"))))
                .isEqualTo("SHOP_ALREADY_EXISTS");

        String seller = login(email);
        assertThat(api.get("/seller/shop", seller).getBody().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(errorCode(api.post("/seller/products", seller, productBody(List.of("Đỏ", "Xanh"), null))))
                .isEqualTo("SHOP_NOT_ACTIVE");

        // 2. Admin duyệt shop
        String admin = api.login("admin@shopvi.local");
        JsonNode pending = api.get("/admin/shops?status=PENDING&size=100", admin).getBody().at("/data/items");
        assertThat(pending.findValuesAsText("id")).contains(String.valueOf(shopId));
        assertThat(api.post("/admin/shops/" + shopId + "/approve", admin, null).getBody().at("/data/status").asText())
                .isEqualTo("ACTIVE");
        assertThat(errorCode(api.post("/admin/shops/" + shopId + "/approve", admin, null)))
                .isEqualTo("INVALID_SHOP_STATUS");
        assertThat(api.post("/admin/shops/" + shopId + "/approve", seller, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Seller đăng sản phẩm 2 màu × 2 size = 4 SKU
        var created = api.post("/seller/products", seller, productBody(List.of("Đỏ", "Xanh"), "IT-" + shopId + "-DO-M"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode product = created.getBody().at("/data");
        long productId = product.at("/id").asLong();
        String slug = product.at("/slug").asText();
        assertThat(slug).endsWith("-" + productId);
        assertThat(product.at("/status").asText()).isEqualTo("ACTIVE");
        assertThat(product.at("/variants")).hasSize(4);
        assertThat(product.at("/minPrice").asLong()).isEqualTo(150_000);
        assertThat(product.at("/maxPrice").asLong()).isEqualTo(170_000);
        assertThat(product.at("/totalStock").asInt()).isEqualTo(40);
        assertThat(product.at("/variants").findValuesAsText("sku")).contains("IT-" + shopId + "-DO-M");

        // Hiển thị phía người mua
        JsonNode publicDetail = api.get("/products/" + slug, null).getBody().at("/data");
        assertThat(publicDetail.at("/variants")).hasSize(4);
        assertThat(publicDetail.at("/shop/id").asLong()).isEqualTo(shopId);
        assertThat(publicDetail.at("/variantOptions/0/name").asText()).isEqualTo("Màu sắc");

        // Ẩn → người mua không thấy; hiện lại
        assertThat(api.exchange(HttpMethod.PATCH, "/seller/products/" + productId + "/visibility", seller,
                Map.of("visible", false), Map.of()).getBody().at("/data/status").asText()).isEqualTo("HIDDEN");
        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        api.exchange(HttpMethod.PATCH, "/seller/products/" + productId + "/visibility", seller,
                Map.of("visible", true), Map.of());
        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Sửa: bỏ màu Xanh, giữ SKU màu Đỏ (theo id) và đổi giá → còn 2 SKU
        Map<String, Object> update = productBody(List.of("Đỏ"), null);
        List<Map<String, Object>> keptVariants = new ArrayList<>();
        for (JsonNode v : product.at("/variants")) {
            if (v.at("/optionValues/Màu sắc").asText().equals("Đỏ")) {
                keptVariants.add(Map.of("id", v.at("/id").asLong(), "sku", v.at("/sku").asText(),
                        "optionValues", Map.of("Màu sắc", "Đỏ", "Size", v.at("/optionValues/Size").asText()),
                        "price", 199_000, "stock", 20));
            }
        }
        update.put("variants", keptVariants);
        JsonNode updated = api.put("/seller/products/" + productId, seller, update).getBody().at("/data");
        assertThat(updated.at("/variants")).hasSize(2);
        assertThat(updated.at("/minPrice").asLong()).isEqualTo(199_000);
        assertThat(updated.at("/slug").asText()).isEqualTo(slug);

        // Seller khác không thấy / không sửa được sản phẩm này
        String otherSeller = api.login("seller1@shopvi.local");
        assertThat(api.get("/seller/products/" + productId, otherSeller).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.put("/seller/products/" + productId, otherSeller, update).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode myList = api.get("/seller/products?q=IT cotton", seller).getBody().at("/data");
        assertThat(myList.at("/totalItems").asLong()).isEqualTo(1);
        assertThat(myList.at("/items/0/totalStock").asInt()).isEqualTo(40);
        assertThat(myList.at("/items/0/variantCount").asInt()).isEqualTo(2);

        // 4. Người mua đặt hàng 2 cái
        long variantId = updated.at("/variants/0/id").asLong();
        String buyer = api.registerRandom();
        long addressId = createAddress(buyer);
        api.post("/cart/items", buyer, Map.of("variantId", variantId, "quantity", 2));
        String code = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "COD"))
                .getBody().at("/data/orders/0/code").asText();

        // 5. Seller xử lý đơn: Chờ xác nhận → Chờ lấy hàng → Đang giao → Đã giao
        JsonNode orders = api.get("/seller/orders?status=PENDING", seller).getBody().at("/data");
        assertThat(orders.at("/totalItems").asLong()).isEqualTo(1);
        assertThat(orders.at("/items/0/code").asText()).isEqualTo(code);
        assertThat(orders.at("/items/0/receiverName").asText()).isEqualTo("Người nhận");
        assertThat(texts(orders.at("/items/0/actions"))).containsExactly("CONFIRM", "CANCEL");
        assertThat(api.get("/seller/orders/counts", seller).getBody().at("/data/PENDING").asLong()).isEqualTo(1);
        assertThat(api.get("/seller/orders/" + code, otherSeller).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCode(api.post("/seller/orders/" + code + "/deliver", seller, null)))
                .isEqualTo("INVALID_ORDER_STATUS_TRANSITION");

        api.post("/seller/orders/" + code + "/confirm", seller, null);
        api.post("/seller/orders/" + code + "/ship", seller, Map.of("note", "Mã vận đơn GHN123"));
        JsonNode delivered = api.post("/seller/orders/" + code + "/deliver", seller, null).getBody().at("/data");
        assertThat(delivered.at("/status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.at("/paymentStatus").asText()).isEqualTo("PAID");
        assertThat(delivered.at("/history")).hasSize(4);
        assertThat(delivered.at("/history/2/note").asText()).isEqualTo("Mã vận đơn GHN123");
        assertThat(delivered.at("/actions")).isEmpty();
        assertThat(api.get("/orders/" + code, buyer).getBody().at("/data/status").asText()).isEqualTo("DELIVERED");

        // 6. Dashboard: doanh số = tiền hàng của đơn, top sản phẩm là sản phẩm vừa bán
        JsonNode dashboard = api.get("/seller/dashboard", seller).getBody().at("/data");
        assertThat(dashboard.at("/series")).hasSize(30);
        assertThat(dashboard.at("/summary/revenue").asLong()).isEqualTo(2 * 199_000);
        assertThat(dashboard.at("/summary/orders").asLong()).isEqualTo(1);
        assertThat(dashboard.at("/summary/unitsSold").asLong()).isEqualTo(2);
        assertThat(dashboard.at("/statusCounts/DELIVERED").asLong()).isEqualTo(1);
        assertThat(dashboard.at("/topProducts/0/productId").asLong()).isEqualTo(productId);
        assertThat(dashboard.at("/topProducts/0/quantity").asLong()).isEqualTo(2);
        JsonNode monthly = api.get("/seller/dashboard?granularity=MONTH", seller).getBody().at("/data");
        assertThat(monthly.at("/series")).hasSize(12);
        assertThat(monthly.at("/summary/revenue").asLong()).isEqualTo(2 * 199_000);
        assertThat(errorCode(api.get("/seller/dashboard?from=2026-01-10&to=2026-01-01", seller)))
                .isEqualTo("VALIDATION_ERROR");

        // 7. Seller hủy đơn mới (kèm lý do) → hoàn kho
        int stockBefore = stock(variantId);
        api.post("/cart/items", buyer, Map.of("variantId", variantId, "quantity", 3));
        String second = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "COD"))
                .getBody().at("/data/orders/0/code").asText();
        assertThat(stock(variantId)).isEqualTo(stockBefore - 3);
        assertThat(errorCode(api.post("/seller/orders/" + second + "/cancel", seller, Map.of("reason", " "))))
                .isEqualTo("VALIDATION_ERROR");
        JsonNode cancelled = api.post("/seller/orders/" + second + "/cancel", seller, Map.of("reason", "Hết hàng"))
                .getBody().at("/data");
        assertThat(cancelled.at("/status").asText()).isEqualTo("CANCELLED");
        assertThat(stock(variantId)).isEqualTo(stockBefore);
        assertThat(api.get("/orders/" + second, buyer).getBody().at("/data/history/1/note").asText()).isEqualTo("Hết hàng");

        // 8. Xóa sản phẩm → biến mất khỏi sàn và danh sách của seller
        assertThat(api.delete("/seller/products/" + productId, seller).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.get("/seller/products/" + productId, seller).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectedShopCanBeResubmitted() {
        String email = randomEmail();
        String token = register(email);
        long shopId = api.post("/seller/shop/register", token, Map.of("name", "Shop Bị Từ Chối"))
                .getBody().at("/data/id").asLong();
        String admin = api.login("admin@shopvi.local");
        assertThat(errorCode(api.post("/admin/shops/" + shopId + "/reject", admin, Map.of("reason", ""))))
                .isEqualTo("VALIDATION_ERROR");
        JsonNode rejected = api.post("/admin/shops/" + shopId + "/reject", admin, Map.of("reason", "Tên shop chưa phù hợp"))
                .getBody().at("/data");
        assertThat(rejected.at("/status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.at("/owner/email").asText()).isEqualTo(email);

        String seller = login(email);
        JsonNode mine = api.get("/seller/shop", seller).getBody().at("/data");
        assertThat(mine.at("/status").asText()).isEqualTo("REJECTED");
        assertThat(mine.at("/rejectReason").asText()).isEqualTo("Tên shop chưa phù hợp");

        JsonNode resubmitted = api.put("/seller/shop", seller, Map.of("name", "Shop Đã Sửa Tên " + shopId))
                .getBody().at("/data");
        assertThat(resubmitted.at("/status").asText()).isEqualTo("PENDING");
        assertThat(resubmitted.at("/rejectReason").isNull()).isTrue();
        assertThat(resubmitted.at("/slug").asText()).isEqualTo("shop-da-sua-ten-" + shopId);
    }

    @Test
    void productInputIsValidated() {
        String seller = api.login("seller1@shopvi.local");

        // Thiếu tổ hợp (Xanh) → 400
        Map<String, Object> missing = productBody(List.of("Đỏ", "Xanh"), null);
        missing.put("variants", ((List<?>) missing.get("variants")).subList(0, 2));
        var res = api.post("/seller/products", seller, missing);
        assertThat(errorCode(res)).isEqualTo("VALIDATION_ERROR");
        assertThat(res.getBody().at("/error/details/0/field").asText()).isEqualTo("variants");

        // URL ảnh không phải http(s)
        Map<String, Object> badImage = productBody(List.of("Đỏ"), null);
        badImage.put("images", List.of("javascript:alert(1)"));
        res = api.post("/seller/products", seller, badImage);
        assertThat(errorCode(res)).isEqualTo("VALIDATION_ERROR");
        assertThat(res.getBody().at("/error/details/0/field").asText()).startsWith("images");

        // Danh mục cha (không phải cấp thấp nhất)
        Map<String, Object> rootCategory = productBody(List.of("Đỏ"), null);
        rootCategory.put("categoryId", jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'thoi-trang-nam'",
                Long.class));
        assertThat(errorCode(api.post("/seller/products", seller, rootCategory))).isEqualTo("CATEGORY_NOT_LEAF");

        // SKU đã dùng ở sản phẩm khác
        Map<String, Object> takenSku = productBody(List.of("Đỏ"), jdbc.queryForObject(
                "SELECT sku FROM product_variants ORDER BY id LIMIT 1", String.class));
        assertThat(errorCode(api.post("/seller/products", seller, takenSku))).isEqualTo("SKU_ALREADY_EXISTS");

        // Giá dưới 1.000đ
        Map<String, Object> cheap = productBody(List.of("Đỏ"), null);
        @SuppressWarnings("unchecked")
        var variants = (List<Map<String, Object>>) cheap.get("variants");
        variants.get(0).put("price", 500);
        assertThat(errorCode(api.post("/seller/products", seller, cheap))).isEqualTo("VALIDATION_ERROR");

        // Người mua không gọi được API seller
        assertThat(api.get("/seller/products", api.registerRandom()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers

    /** Áo thun: màu (theo tham số) × size [M, L]; giá 150.000đ (M) / 170.000đ (L), mỗi SKU 10 cái. */
    private Map<String, Object> productBody(List<String> colors, String firstSku) {
        long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'ao-thun-nam'", Long.class);
        List<Map<String, Object>> variants = new ArrayList<>();
        for (String color : colors) {
            for (String size : List.of("M", "L")) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("optionValues", Map.of("Màu sắc", color, "Size", size));
                v.put("price", size.equals("M") ? 150_000 : 170_000);
                v.put("stock", 10);
                if (variants.isEmpty() && firstSku != null) {
                    v.put("sku", firstSku);
                }
                variants.add(v);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", categoryId);
        body.put("name", "Áo thun IT cotton");
        body.put("description", "Áo thun cotton 100%");
        body.put("attributes", Map.of("Chất liệu", "Cotton"));
        body.put("images", List.of("https://picsum.photos/seed/it-1/800/800", "https://picsum.photos/seed/it-2/800/800"));
        body.put("variantOptions", List.of(Map.of("name", "Màu sắc", "values", colors),
                Map.of("name", "Size", "values", List.of("M", "L"))));
        body.put("variants", variants);
        return body;
    }

    private static String randomEmail() {
        return "it-seller-" + UUID.randomUUID() + "@example.com";
    }

    private String register(String email) {
        return api.post("/auth/register", null, Map.of("email", email, "password", PASSWORD, "fullName", "IT Seller"))
                .getBody().at("/data/accessToken").asText();
    }

    private String login(String email) {
        return api.post("/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .getBody().at("/data/accessToken").asText();
    }

    private long createAddress(String token) {
        return api.post("/me/addresses", token, Map.of("receiverName", "Người nhận", "phone", "0901234567",
                "provinceCode", "79", "wardCode", "26740", "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
    }

    private int stock(long variantId) {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private static String errorCode(ResponseEntity<JsonNode> res) {
        return res.getBody().at("/error/code").asText();
    }

    private static List<String> texts(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.asText()));
        return result;
    }
}
