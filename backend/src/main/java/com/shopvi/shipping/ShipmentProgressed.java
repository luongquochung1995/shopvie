package com.shopvi.shipping;

/** Sự kiện: hãng vận chuyển báo vận đơn của đơn hàng đổi trạng thái (từ webhook). */
public record ShipmentProgressed(String orderCode, ShipmentStatus status, String description) {
}
