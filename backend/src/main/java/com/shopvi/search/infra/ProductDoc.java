package com.shopvi.search.infra;

import com.shopvi.catalog.SearchDocument;
import java.util.List;

/** Tài liệu sản phẩm trong Elasticsearch (thời gian dạng epoch millis để không phụ thuộc cấu hình Jackson). */
public record ProductDoc(Long id, String name, String slug, List<Long> categoryIds, List<String> categoryNames,
                         Long shopId, String shopName, String keywords, String thumbnailUrl, long minPrice,
                         long maxPrice, int soldCount, double ratingAvg, int ratingCount, long createdAt) {

    public static ProductDoc of(SearchDocument d) {
        return new ProductDoc(d.id(), d.name(), d.slug(), d.categoryIds(), d.categoryNames(), d.shopId(), d.shopName(),
                d.keywords(), d.thumbnailUrl(), d.minPrice(), d.maxPrice(), d.soldCount(),
                d.ratingAvg() == null ? 0 : d.ratingAvg().doubleValue(), d.ratingCount(),
                d.createdAt() == null ? 0 : d.createdAt().toEpochMilli());
    }
}
