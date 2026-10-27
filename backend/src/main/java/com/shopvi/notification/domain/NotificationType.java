package com.shopvi.notification.domain;

/** Loại thông báo — FE hiển thị nội dung theo loại + tham số (đa ngôn ngữ). */
public enum NotificationType {
    /** Người mua: đơn đổi trạng thái (params: orderCode, status, note). */
    ORDER_STATUS,
    /** Seller: người mua hủy đơn (orderCode, note). */
    ORDER_CANCELLED_BY_BUYER,
    /** Seller: có đơn mới cần xử lý (orderCode, total). */
    NEW_ORDER,
    /** Người mua: thanh toán online thành công (orderCode, total). */
    PAYMENT_SUCCEEDED,
    /** Seller: shop được duyệt / bị từ chối / bị khóa / được mở khóa (shopName, reason). */
    SHOP_APPROVED, SHOP_REJECTED, SHOP_BANNED, SHOP_UNBANNED,
    /** Seller: sản phẩm bị khóa / được mở khóa (productName, reason). */
    PRODUCT_BANNED, PRODUCT_UNBANNED
}
