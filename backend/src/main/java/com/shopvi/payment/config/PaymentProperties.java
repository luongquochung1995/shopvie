package com.shopvi.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Cấu hình thanh toán online (prefix {@code app.payment}). Cổng nào thiếu key thì không được bật.
 *
 * @param publicBaseUrl   URL gốc backend mà cổng thanh toán gọi về (return URL, IPN), vd. https://api.shopvi.vn
 * @param frontendBaseUrl URL gốc frontend để chuyển người mua về trang kết quả, vd. https://shopvi.vn
 * @param ttl             thời hạn thanh toán; quá hạn thì đơn bị hủy và hoàn kho
 * @param sandboxGateway  chỉ dùng ở dev/test: thay trang của cổng bằng trang giả lập của ShopVi, callback vẫn được
 *                        ký bằng key cấu hình và đi qua đúng luồng kiểm tra chữ ký như thật
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(String publicBaseUrl, String frontendBaseUrl, Duration ttl, boolean sandboxGateway,
                                Vnpay vnpay, Momo momo) {

    public PaymentProperties {
        ttl = ttl == null ? Duration.ofMinutes(15) : ttl;
        vnpay = vnpay == null ? new Vnpay(null, null, null) : vnpay;
        momo = momo == null ? new Momo(null, null, null, null) : momo;
    }

    /** @param payUrl vd. https://sandbox.vnpayment.vn/paymentv2/vpcpay.html */
    public record Vnpay(String tmnCode, String hashSecret, String payUrl) {
        public boolean configured() {
            return StringUtils.hasText(tmnCode) && StringUtils.hasText(hashSecret);
        }
    }

    /** @param endpoint vd. https://test-payment.momo.vn/v2/gateway/api/create */
    public record Momo(String partnerCode, String accessKey, String secretKey, String endpoint) {
        public boolean configured() {
            return StringUtils.hasText(partnerCode) && StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
        }
    }

    @Configuration
    @EnableConfigurationProperties(PaymentProperties.class)
    static class Config {
    }
}
