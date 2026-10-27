package com.shopvi.payment.infra;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.application.PaymentProvider;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * VNPay (API thanh toán v2.1.0). Chữ ký: HMAC-SHA512 trên chuỗi {@code key=value&...} sắp theo tên tham số,
 * giá trị URL-encode (US-ASCII, khoảng trắng thành '+') — giống code mẫu của VNPay.
 * Thành công khi {@code vnp_ResponseCode = 00} và {@code vnp_TransactionStatus = 00}.
 */
@Component
@RequiredArgsConstructor
class VnpayProvider implements PaymentProvider {

    static final String VERSION = "2.1.0";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VN);

    private final PaymentProperties properties;
    private final Clock clock;

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.VNPAY;
    }

    @Override
    public boolean isEnabled() {
        return properties.vnpay().configured();
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp, String returnUrl, String ipnUrl) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.vnpay().tmnCode());
        params.put("vnp_Amount", String.valueOf(payment.getAmount() * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getIdempotencyKey());
        // US-ASCII theo đặc tả: không dùng tiếng Việt có dấu
        params.put("vnp_OrderInfo", "Thanh toan don hang ShopVi " + payment.getCheckoutId());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        params.put("vnp_CreateDate", TIME.format(clock.instant()));
        params.put("vnp_ExpireDate", TIME.format(payment.getExpiresAt()));
        String query = encode(params);
        return properties.vnpay().payUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    @Override
    public CallbackData parseCallback(Map<String, String> params) {
        Map<String, String> fields = new TreeMap<>(params);
        String hash = fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");
        fields.values().removeIf(v -> v == null || v.isEmpty());
        if (!Hmac.matches(sign(encode(fields)), hash)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }
        long amount;
        try {
            amount = Long.parseLong(fields.getOrDefault("vnp_Amount", "0")) / 100;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Invalid amount");
        }
        String code = fields.get("vnp_ResponseCode");
        boolean success = "00".equals(code) && "00".equals(fields.get("vnp_TransactionStatus"));
        return new CallbackData(fields.get("vnp_TxnRef"), success, amount, fields.get("vnp_TransactionNo"), code,
                Map.copyOf(params));
    }

    @Override
    public Map<String, String> signedCallback(Payment payment, boolean success, Instant now) {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TmnCode", properties.vnpay().tmnCode());
        params.put("vnp_Amount", String.valueOf(payment.getAmount() * 100));
        params.put("vnp_BankCode", success ? "NCB" : "VNPAY");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don hang ShopVi " + payment.getCheckoutId());
        params.put("vnp_PayDate", TIME.format(now));
        // 24 = khách hàng hủy giao dịch (bảng mã lỗi VNPay)
        params.put("vnp_ResponseCode", success ? "00" : "24");
        params.put("vnp_TransactionStatus", success ? "00" : "02");
        params.put("vnp_TransactionNo", String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L)));
        params.put("vnp_TxnRef", payment.getIdempotencyKey());
        params.put("vnp_SecureHash", sign(encode(params)));
        return params;
    }

    String sign(String data) {
        return Hmac.sha512(properties.vnpay().hashSecret(), data);
    }

    /** Chuỗi query đã sắp xếp theo tên tham số (dùng cho cả ký và URL). */
    static String encode(Map<String, String> sorted) {
        return sorted.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }
}
