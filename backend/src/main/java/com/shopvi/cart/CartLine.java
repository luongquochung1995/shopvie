package com.shopvi.cart;

/**
 * Một dòng giỏ hàng được chọn để thanh toán.
 */
public record CartLine(Long itemId, Long variantId, int quantity) {
}
