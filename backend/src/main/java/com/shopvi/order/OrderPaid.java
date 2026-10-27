package com.shopvi.order;

/** Sự kiện: đơn thanh toán online đã được cổng xác nhận thanh toán. */
public record OrderPaid(String orderCode, Long buyerId, Long shopId, long total) {
}
