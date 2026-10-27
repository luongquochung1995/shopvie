package com.shopvi.catalog;

/** Sự kiện: admin khóa ({@code banned = true}) hoặc mở khóa sản phẩm. */
public record ProductModerated(Long productId, Long shopId, String productName, boolean banned, String reason) {
}
