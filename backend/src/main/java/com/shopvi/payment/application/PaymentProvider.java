package com.shopvi.payment.application;

import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.domain.Payment;
import java.time.Instant;
import java.util.Map;

/**
 * Cổng thanh toán (strategy pattern): mỗi cổng tự tạo URL thanh toán, ký và kiểm tra chữ ký callback theo đặc tả riêng.
 */
public interface PaymentProvider {

    PaymentChannel channel();

    /** Đủ cấu hình (mã merchant + khóa bí mật) để dùng. */
    boolean isEnabled();

    /** URL trang thanh toán của cổng cho giao dịch (MoMo cần gọi API tạo giao dịch trước). */
    String createPaymentUrl(Payment payment, String clientIp, String returnUrl, String ipnUrl);

    /**
     * Kiểm tra chữ ký và đọc kết quả từ tham số callback (return URL hoặc IPN).
     * Ném {@code INVALID_SIGNATURE} nếu chữ ký sai hoặc thiếu.
     */
    CallbackData parseCallback(Map<String, String> params);

    /** Chỉ dùng cho cổng giả lập ở dev/test: tham số callback đã ký, giống hệt cổng thật gửi về. */
    Map<String, String> signedCallback(Payment payment, boolean success, Instant now);

    /**
     * Kết quả giao dịch do cổng báo về.
     *
     * @param reference mã giao dịch của ShopVi (idempotency key)
     * @param amount    số tiền (VND) cổng đã thu
     */
    record CallbackData(String reference, boolean success, long amount, String providerTxnId, String resultCode,
                        Map<String, String> raw) {
    }
}
