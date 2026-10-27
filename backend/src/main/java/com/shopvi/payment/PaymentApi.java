package com.shopvi.payment;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * API công khai của module payment (thanh toán online qua cổng VNPay/MoMo).
 * Thanh toán thành công được báo cho module khác qua sự kiện {@link PaymentSucceeded} (cùng transaction).
 */
public interface PaymentApi {

    /** Thời hạn thanh toán kể từ lúc đặt hàng; quá hạn thì đơn bị hủy. */
    Duration paymentTtl();

    /** Cổng thanh toán đang cấu hình đủ để dùng. */
    Set<PaymentChannel> availableChannels();

    /**
     * Tạo giao dịch PENDING và URL chuyển người mua sang cổng thanh toán. Chạy trong transaction của nơi gọi.
     * Ném {@code PAYMENT_METHOD_NOT_SUPPORTED} nếu cổng chưa cấu hình, {@code PAYMENT_PROVIDER_ERROR} nếu cổng lỗi.
     */
    PaymentLink initiate(PaymentRequest request);

    /** Trạng thái giao dịch gần nhất của checkout (empty nếu chưa từng thanh toán online). */
    Optional<PaymentState> latestState(UUID checkoutId);

    /** Đánh dấu các giao dịch còn PENDING của checkout là hết hạn (khi đơn bị hủy vì quá hạn thanh toán). */
    void expire(UUID checkoutId);
}
