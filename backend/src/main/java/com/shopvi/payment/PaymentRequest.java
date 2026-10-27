package com.shopvi.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * Yêu cầu thanh toán toàn bộ một checkout (có thể gồm nhiều đơn của nhiều shop).
 *
 * @param amount    tổng tiền (VND)
 * @param clientIp  IP người mua (VNPay yêu cầu)
 * @param expiresAt hạn chót thanh toán của checkout (thanh toán lại không được kéo dài hạn)
 */
public record PaymentRequest(UUID checkoutId, Long userId, PaymentChannel channel, long amount, String clientIp,
                             Instant expiresAt) {
}
