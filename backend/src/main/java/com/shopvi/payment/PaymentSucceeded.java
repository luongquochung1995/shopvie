package com.shopvi.payment;

import java.util.UUID;

/**
 * Sự kiện: checkout đã được thanh toán thành công (phát trong transaction xác nhận IPN/return URL,
 * chỉ một lần cho mỗi giao dịch — IPN gọi lặp không phát lại).
 */
public record PaymentSucceeded(UUID checkoutId, PaymentChannel channel, long amount, String providerTxnId) {
}
