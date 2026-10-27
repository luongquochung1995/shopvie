package com.shopvi.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Dữ liệu một sản phẩm đang bán để đưa vào chỉ mục tìm kiếm.
 *
 * @param categoryIds   danh mục của sản phẩm và mọi danh mục cha (lọc theo danh mục cha)
 * @param categoryNames tên các danh mục đó (tìm theo tên danh mục)
 * @param keywords      giá trị thuộc tính + phân loại, vd. "Coolmate Cotton Đỏ Xanh M L"
 */
public record SearchDocument(Long id, String name, String slug, List<Long> categoryIds, List<String> categoryNames,
                             Long shopId, String shopName, String keywords, String thumbnailUrl, long minPrice,
                             long maxPrice, int soldCount, BigDecimal ratingAvg, int ratingCount, Instant createdAt) {
}
