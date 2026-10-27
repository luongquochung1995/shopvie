package com.shopvi.payment;

import java.time.Instant;

/** URL chuyển người mua sang cổng thanh toán và hạn chót thanh toán. */
public record PaymentLink(String paymentUrl, Instant expiresAt) {
}
