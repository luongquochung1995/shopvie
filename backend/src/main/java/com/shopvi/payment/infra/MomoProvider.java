package com.shopvi.payment.infra;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.application.PaymentProvider;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * MoMo (API v2, requestType {@code captureWallet}). Chữ ký HMAC-SHA256 trên chuỗi {@code key=value&...} theo thứ tự
 * tên trường a→z (không encode) do MoMo quy định; callback (redirect và IPN) dùng cùng bộ trường.
 * Thành công khi {@code resultCode = 0} (hoặc 9000: đã xác nhận).
 */
@Slf4j
@Component
class MomoProvider implements PaymentProvider {

    static final String REQUEST_TYPE = "captureWallet";
    /** Trường tham gia chữ ký callback, đúng thứ tự MoMo quy định. */
    static final List<String> CALLBACK_FIELDS = List.of("accessKey", "amount", "extraData", "message", "orderId",
            "orderInfo", "orderType", "partnerCode", "payType", "requestId", "responseTime", "resultCode", "transId");

    private final PaymentProperties properties;
    private final RestClient rest;
    private final Clock clock;

    MomoProvider(PaymentProperties properties, RestClient.Builder builder, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        // Timeout theo spring.http.client.* (application.yml)
        this.rest = builder.build();
    }

    @Override
    public PaymentChannel channel() {
        return PaymentChannel.MOMO;
    }

    @Override
    public boolean isEnabled() {
        return properties.momo().configured();
    }

    @Override
    public String createPaymentUrl(Payment payment, String clientIp, String returnUrl, String ipnUrl) {
        PaymentProperties.Momo momo = properties.momo();
        String orderInfo = orderInfo(payment);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", momo.partnerCode());
        body.put("requestId", payment.getIdempotencyKey());
        body.put("amount", payment.getAmount());
        body.put("orderId", payment.getIdempotencyKey());
        body.put("orderInfo", orderInfo);
        body.put("redirectUrl", returnUrl);
        body.put("ipnUrl", ipnUrl);
        body.put("requestType", REQUEST_TYPE);
        body.put("extraData", "");
        body.put("lang", "vi");
        body.put("orderExpireTime", Math.max(1, Duration.between(clock.instant(), payment.getExpiresAt()).toMinutes()));
        String raw = "accessKey=" + momo.accessKey() + "&amount=" + payment.getAmount() + "&extraData="
                + "&ipnUrl=" + ipnUrl + "&orderId=" + payment.getIdempotencyKey() + "&orderInfo=" + orderInfo
                + "&partnerCode=" + momo.partnerCode() + "&redirectUrl=" + returnUrl
                + "&requestId=" + payment.getIdempotencyKey() + "&requestType=" + REQUEST_TYPE;
        body.put("signature", Hmac.sha256(momo.secretKey(), raw));
        try {
            Map<?, ?> res = rest.post().uri(momo.endpoint()).body(body).retrieve().body(Map.class);
            Object resultCode = res == null ? null : res.get("resultCode");
            Object payUrl = res == null ? null : res.get("payUrl");
            if (!"0".equals(String.valueOf(resultCode)) || payUrl == null) {
                log.warn("MoMo create payment failed: resultCode={}, message={}", resultCode,
                        res == null ? null : res.get("message"));
                throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
            }
            return payUrl.toString();
        } catch (RestClientException ex) {
            log.warn("MoMo create payment error: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_PROVIDER_ERROR);
        }
    }

    @Override
    public CallbackData parseCallback(Map<String, String> params) {
        if (!Hmac.matches(sign(params), params.get("signature"))) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }
        long amount;
        try {
            amount = Long.parseLong(params.getOrDefault("amount", "0"));
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Invalid amount");
        }
        String code = params.get("resultCode");
        boolean success = "0".equals(code) || "9000".equals(code);
        return new CallbackData(params.get("orderId"), success, amount, params.get("transId"), code, Map.copyOf(params));
    }

    @Override
    public Map<String, String> signedCallback(Payment payment, boolean success, Instant now) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("partnerCode", properties.momo().partnerCode());
        params.put("orderId", payment.getIdempotencyKey());
        params.put("requestId", payment.getIdempotencyKey());
        params.put("amount", String.valueOf(payment.getAmount()));
        params.put("orderInfo", orderInfo(payment));
        params.put("orderType", "momo_wallet");
        params.put("transId", String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L)));
        // 1006 = người dùng từ chối xác nhận thanh toán (bảng mã lỗi MoMo)
        params.put("resultCode", success ? "0" : "1006");
        params.put("message", success ? "Successful." : "Transaction denied by user.");
        params.put("payType", "qr");
        params.put("responseTime", String.valueOf(now.toEpochMilli()));
        params.put("extraData", "");
        params.put("signature", sign(params));
        return params;
    }

    private String sign(Map<String, String> params) {
        String raw = CALLBACK_FIELDS.stream()
                .map(f -> f + "=" + (f.equals("accessKey") ? properties.momo().accessKey() : params.getOrDefault(f, "")))
                .collect(Collectors.joining("&"));
        return Hmac.sha256(properties.momo().secretKey(), raw);
    }

    private static String orderInfo(Payment payment) {
        return "Thanh toan don hang ShopVi " + payment.getCheckoutId();
    }
}
