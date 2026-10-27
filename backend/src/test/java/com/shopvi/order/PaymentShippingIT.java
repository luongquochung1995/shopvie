package com.shopvi.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.order.application.OrderPaymentHandler;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 4: thanh toán online qua cổng giả lập (callback ký bằng key test, đi qua đúng luồng kiểm tra chữ ký),
 * IPN idempotent, hủy đơn quá hạn thanh toán, phí ship theo vùng, vận đơn + webhook GHN.
 */
@IntegrationTest
class PaymentShippingIT {

    private static final String VNPAY_SECRET = "it-vnpay-secret";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderPaymentHandler paymentHandler;

    private ApiClient api;
    private long variant;
    private long shopId;
    private int originalStock;
    private Map<String, Object> originalPickup;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT v.id, p.shop_id FROM product_variants v JOIN products p ON p.id = v.product_id
                JOIN shops s ON s.id = p.shop_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND s.status = 'ACTIVE'
                  AND s.owner_id IN (SELECT id FROM users WHERE email LIKE 'seller_@shopvi.local')
                ORDER BY v.id LIMIT 1""");
        variant = ((Number) row.get("id")).longValue();
        shopId = ((Number) row.get("shop_id")).longValue();
        originalStock = stock();
        jdbc.update("UPDATE product_variants SET stock = 50 WHERE id = ?", variant);
        originalPickup = jdbc.queryForMap("SELECT pickup_province_code, pickup_ward_code FROM shops WHERE id = ?", shopId);
        // Kho của shop ở TP.HCM
        jdbc.update("UPDATE shops SET pickup_name = 'Kho IT', pickup_phone = '0901234567', pickup_province_code = '79', "
                + "pickup_ward_code = '26740', pickup_detail = '12 Lê Lợi' WHERE id = ?", shopId);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE product_variants SET stock = ? WHERE id = ?", originalStock, variant);
        jdbc.update("UPDATE shops SET pickup_province_code = ?, pickup_ward_code = ? WHERE id = ?",
                originalPickup.get("pickup_province_code"), originalPickup.get("pickup_ward_code"), shopId);
    }

    @Test
    void shippingFeeDependsOnZone() {
        String buyer = api.registerRandom();
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 1));

        long hcm = address(buyer, "79", "26740");
        assertThat(api.post("/checkout/preview", buyer, Map.of("addressId", hcm)).getBody()
                .at("/data/groups/0/shippingFee").asLong()).isEqualTo(22_000);
        long hanoi = address(buyer, "01", "00004");
        JsonNode preview = api.post("/checkout/preview", buyer, Map.of("addressId", hanoi)).getBody().at("/data");
        assertThat(preview.at("/groups/0/shippingFee").asLong()).isEqualTo(38_000);
        assertThat(preview.at("/paymentMethods").toString()).contains("COD", "VNPAY", "MOMO");
    }

    @Test
    void vnpayPaymentConfirmsOrdersOnceThenSellerShipsWithGhnWebhook() {
        String buyer = api.registerRandom();
        long addressId = address(buyer, "79", "26740");
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 2));
        JsonNode placed = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "VNPAY"))
                .getBody().at("/data");
        String checkoutId = placed.at("/checkoutId").asText();
        String code = placed.at("/orders/0/code").asText();
        String paymentUrl = placed.at("/paymentUrl").asText();
        assertThat(paymentUrl).startsWith("http://localhost:4200/payment/sandbox?ref=").contains("method=VNPAY");
        assertThat(placed.at("/paymentExpiresAt").asText()).isNotBlank();
        assertThat(stock()).isEqualTo(48);

        // Chưa thanh toán: buyer thấy chờ thanh toán, seller chưa xác nhận được
        assertThat(paymentStatus(buyer, checkoutId)).isEqualTo("PENDING");
        JsonNode order = api.get("/orders/" + code, buyer).getBody().at("/data");
        assertThat(order.at("/awaitingPayment").asBoolean()).isTrue();
        String seller = api.login(sellerEmail());
        assertThat(api.get("/seller/orders/" + code, seller).getBody().at("/data/actions").toString())
                .doesNotContain("CONFIRM");
        assertThat(errorCode(api.post("/seller/orders/" + code + "/confirm", seller, null)))
                .isEqualTo("ORDER_AWAITING_PAYMENT");

        // Cổng giả lập: thanh toán thành công → return URL đã ký → chuyển về trang kết quả
        String ref = query(paymentUrl).get("ref");
        assertThat(api.post("/payments/sandbox/" + ref, api.registerRandom(), Map.of("success", true)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        String redirect = api.post("/payments/sandbox/" + ref, buyer, Map.of("success", true))
                .getBody().at("/data/redirectUrl").asText();
        assertThat(redirect).startsWith("http://localhost/api/v1/payments/vnpay/return?");
        ResponseEntity<JsonNode> returned = api.getRaw(pathOf(redirect));
        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(returned.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("http://localhost:4200/payment/result?status=success&checkoutId=" + checkoutId);
        assertThat(paymentStatus(buyer, checkoutId)).isEqualTo("PAID");
        assertThat(api.get("/orders/" + code, buyer).getBody().at("/data/paymentStatus").asText()).isEqualTo("PAID");

        // IPN gọi lặp → 02 (đã xác nhận), không xử lý 2 lần; sai chữ ký → 97; sai số tiền / không có giao dịch
        Map<String, String> ipn = query(redirect);
        assertThat(ipn(ipn).at("/RspCode").asText()).isEqualTo("02");
        Map<String, String> forged = new HashMap<>(ipn);
        forged.put("vnp_Amount", "100");
        assertThat(ipn(forged).at("/RspCode").asText()).isEqualTo("97");
        assertThat(ipn(signed(ipn, Map.of("vnp_Amount", "100"))).at("/RspCode").asText()).isEqualTo("04");
        assertThat(ipn(signed(ipn, Map.of("vnp_TxnRef", "khongton"))).at("/RspCode").asText()).isEqualTo("01");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE checkout_id = ?::uuid AND status = 'SUCCESS'",
                Long.class, checkoutId)).isEqualTo(1);

        // Seller xác nhận → tạo vận đơn (nội bộ vì chưa cấu hình GHN), không thu hộ vì đã thanh toán online
        JsonNode confirmed = api.post("/seller/orders/" + code + "/confirm", seller, null).getBody().at("/data");
        String tracking = confirmed.at("/shipment/trackingCode").asText();
        assertThat(tracking).startsWith("SVX");
        assertThat(confirmed.at("/shipment/provider").asText()).isEqualTo("INTERNAL");
        assertThat(confirmed.at("/shipment/fee").asLong()).isEqualTo(22_000);

        // Webhook hãng vận chuyển: sai token → 401; đang giao → SHIPPING (gửi lặp không đổi); đã giao → DELIVERED
        assertThat(api.post("/shipping/ghn/webhook?token=sai", null, Map.of("OrderCode", tracking, "Status", "delivering"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> delivering = Map.of("OrderCode", tracking, "Status", "delivering",
                "Time", "2026-10-27T02:00:00Z", "Description", "Đang giao hàng");
        assertThat(api.post("/shipping/ghn/webhook?token=it-ghn-token", null, delivering).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        api.post("/shipping/ghn/webhook?token=it-ghn-token", null, delivering);
        assertThat(api.get("/orders/" + code, buyer).getBody().at("/data/status").asText()).isEqualTo("SHIPPING");
        api.post("/shipping/ghn/webhook?token=it-ghn-token", null, Map.of("OrderCode", tracking, "Status", "delivered",
                "Time", "2026-10-28T02:00:00Z"));
        JsonNode delivered = api.get("/orders/" + code, buyer).getBody().at("/data");
        assertThat(delivered.at("/status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.at("/shipment/status").asText()).isEqualTo("DELIVERED");
        assertThat(delivered.at("/shipment/events")).hasSize(3);
        assertThat(delivered.at("/shipment/events/0/description").asText()).isEqualTo("Giao hàng thành công");
        // Webhook cho vận đơn lạ vẫn 200 (để hãng không gửi lại)
        assertThat(api.post("/shipping/ghn/webhook?token=it-ghn-token", null, Map.of("OrderCode", "LA", "Status", "picked"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void failedMomoPaymentCanBeRetried() {
        String buyer = api.registerRandom();
        long addressId = address(buyer, "79", "26740");
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 1));
        JsonNode placed = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "MOMO"))
                .getBody().at("/data");
        String checkoutId = placed.at("/checkoutId").asText();

        String failRedirect = api.post("/payments/sandbox/" + query(placed.at("/paymentUrl").asText()).get("ref"), buyer,
                Map.of("success", false)).getBody().at("/data/redirectUrl").asText();
        assertThat(api.getRaw(pathOf(failRedirect)).getHeaders().getFirst(HttpHeaders.LOCATION)).contains("status=failed");
        assertThat(paymentStatus(buyer, checkoutId)).isEqualTo("FAILED");

        JsonNode retry = api.post("/checkout/" + checkoutId + "/payment", buyer, null).getBody().at("/data");
        String ref = query(retry.at("/paymentUrl").asText()).get("ref");
        assertThat(retry.at("/expiresAt").asText()).isEqualTo(placed.at("/paymentExpiresAt").asText());
        String okRedirect = api.post("/payments/sandbox/" + ref, buyer, Map.of("success", true))
                .getBody().at("/data/redirectUrl").asText();
        // MoMo IPN (POST JSON) cho cùng giao dịch: lần đầu xác nhận, lần sau vẫn 204
        Map<String, Object> ipn = new LinkedHashMap<>(query(okRedirect));
        assertThat(api.post("/payments/momo/ipn", null, ipn).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(api.post("/payments/momo/ipn", null, ipn).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(paymentStatus(buyer, checkoutId)).isEqualTo("PAID");
        assertThat(errorCode(api.post("/checkout/" + checkoutId + "/payment", buyer, null)))
                .isEqualTo("PAYMENT_NOT_REQUIRED");
    }

    @Test
    void unpaidOrderIsCancelledAfterDeadlineAndStockRestored() {
        String buyer = api.registerRandom();
        long addressId = address(buyer, "79", "26740");
        api.post("/cart/items", buyer, Map.of("variantId", variant, "quantity", 3));
        JsonNode placed = api.post("/checkout", buyer, Map.of("addressId", addressId, "paymentMethod", "VNPAY"))
                .getBody().at("/data");
        String checkoutId = placed.at("/checkoutId").asText();
        String code = placed.at("/orders/0/code").asText();
        assertThat(stock()).isEqualTo(47);

        paymentHandler.expireOverdue();
        assertThat(api.get("/orders/" + code, buyer).getBody().at("/data/status").asText()).isEqualTo("PENDING");

        jdbc.update("UPDATE orders SET created_at = now() - interval '20 minutes' WHERE checkout_id = ?::uuid", checkoutId);
        assertThat(paymentHandler.expireOverdue()).isGreaterThanOrEqualTo(1);

        JsonNode order = api.get("/orders/" + code, buyer).getBody().at("/data");
        assertThat(order.at("/status").asText()).isEqualTo("CANCELLED");
        assertThat(order.at("/history/1/note").asText()).isEqualTo("Quá hạn thanh toán");
        assertThat(stock()).isEqualTo(50);
        assertThat(paymentStatus(buyer, checkoutId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE checkout_id = ?::uuid", String.class, checkoutId))
                .isEqualTo("EXPIRED");
        assertThat(errorCode(api.post("/checkout/" + checkoutId + "/payment", buyer, null)))
                .isEqualTo("PAYMENT_NOT_REQUIRED");
    }

    // ----- helpers

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM product_variants WHERE id = ?", Integer.class, variant);
    }

    private String sellerEmail() {
        return jdbc.queryForObject("SELECT u.email FROM users u JOIN shops s ON s.owner_id = u.id WHERE s.id = ?",
                String.class, shopId);
    }

    private long address(String token, String province, String ward) {
        return api.post("/me/addresses", token, Map.of("receiverName", "Người nhận", "phone", "0901234567",
                "provinceCode", province, "wardCode", ward, "detail", "1 Lê Lợi", "isDefault", true))
                .getBody().at("/data/id").asLong();
    }

    private String paymentStatus(String token, String checkoutId) {
        return api.get("/checkout/" + checkoutId + "/payment", token).getBody().at("/data/status").asText();
    }

    private JsonNode ipn(Map<String, String> params) {
        return api.getRaw("/payments/vnpay/ipn?" + encode(params)).getBody();
    }

    /** Ký lại tham số VNPay (sau khi sửa) bằng key test — mô phỏng VNPay gửi dữ liệu khác với giao dịch. */
    private static Map<String, String> signed(Map<String, String> base, Map<String, String> changes) {
        Map<String, String> params = new TreeMap<>(base);
        params.remove("vnp_SecureHash");
        params.putAll(changes);
        String data = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
        params.put("vnp_SecureHash", hmacSha512(VNPAY_SECRET, data));
        return params;
    }

    private static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = URI.create(url).getRawQuery();
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    private static String encode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** "http://localhost/api/v1/payments/..." → "/payments/..." (ApiClient đã có tiền tố /api/v1). */
    private static String pathOf(String absoluteUrl) {
        URI uri = URI.create(absoluteUrl);
        return uri.getRawPath().replaceFirst("^/api/v1", "") + "?" + uri.getRawQuery();
    }

    private static String errorCode(ResponseEntity<JsonNode> res) {
        return res.getBody().at("/error/code").asText();
    }
}
