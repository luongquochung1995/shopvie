package com.shopvi.order.domain;

/**
 * Snapshot voucher đã áp vào đơn (JSONB {@code orders.vouchers}).
 *
 * @param scope  PLATFORM (voucher sàn) | SHOP
 * @param amount số tiền giảm phân bổ cho đơn này (VND)
 */
public record OrderVoucher(String code, String type, String scope, long amount) {
}
