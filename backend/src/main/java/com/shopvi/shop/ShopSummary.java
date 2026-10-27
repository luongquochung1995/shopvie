package com.shopvi.shop;

import java.math.BigDecimal;

/**
 * Thông tin shop rút gọn để hiển thị kèm sản phẩm.
 */
public record ShopSummary(Long id, String name, String slug, String logoUrl, BigDecimal ratingAvg) {
}
