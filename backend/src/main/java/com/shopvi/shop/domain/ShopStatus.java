package com.shopvi.shop.domain;

/**
 * Vòng đời shop:
 * <pre>
 * PENDING ──duyệt──► ACTIVE ──(P6 khóa)──► BANNED
 *    │  ▲
 * từ chối │ seller sửa thông tin, gửi lại
 *    ▼  │
 * REJECTED
 * </pre>
 */
public enum ShopStatus {
    PENDING, ACTIVE, REJECTED, BANNED
}
