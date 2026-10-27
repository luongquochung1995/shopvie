package com.shopvi.shop;

/**
 * Sự kiện: admin đổi trạng thái shop (duyệt / từ chối / khóa / mở khóa).
 *
 * @param from   trạng thái cũ: PENDING | ACTIVE | BANNED
 * @param status trạng thái mới: ACTIVE | REJECTED | BANNED
 * @param reason lý do (từ chối / khóa), null nếu không có
 */
public record ShopStatusChanged(Long shopId, Long ownerId, String shopName, String from, String status, String reason) {
}
