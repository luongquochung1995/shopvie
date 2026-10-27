package com.shopvi.order;

/** Sự kiện: đơn mới được tạo (mỗi shop một đơn). {@code paymentMethod}: COD | VNPAY | MOMO. */
public record OrderPlaced(String orderCode, Long buyerId, Long shopId, long total, String paymentMethod) {
}
