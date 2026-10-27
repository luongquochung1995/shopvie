package com.shopvi.order;

/**
 * Sự kiện: đơn đổi trạng thái (phát trong transaction đổi trạng thái).
 *
 * @param from       trạng thái cũ (tên enum OrderStatus)
 * @param to         trạng thái mới
 * @param changedBy  user thực hiện; null = hệ thống (hết hạn thanh toán, hãng vận chuyển)
 */
public record OrderStatusChanged(String orderCode, Long buyerId, Long shopId, String from, String to, Long changedBy,
                                 String note) {
}
