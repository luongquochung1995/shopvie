package com.shopvi.shipping;

/** Trạng thái vận đơn (chuẩn hóa từ trạng thái riêng của từng hãng). */
public enum ShipmentStatus {
    /** Đã tạo vận đơn, chờ lấy hàng. */
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    /** Giao không thành công (sẽ giao lại hoặc hoàn). */
    FAILED,
    RETURNED,
    CANCELLED
}
