package com.shopvi.catalog.application;

import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.shop.ShopSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dữ liệu đọc của sản phẩm trả cho API public. */
public final class ProductViews {

    private ProductViews() {
    }

    /** Thẻ sản phẩm trong danh sách. */
    public record ProductCard(Long id, String name, String slug, String thumbnailUrl, long minPrice, long maxPrice,
                              int soldCount, BigDecimal ratingAvg, int ratingCount, Instant createdAt) {
    }

    public record CategoryRef(Long id, String name, String slug) {
    }

    public record VariantView(Long id, String sku, Map<String, String> optionValues, long price, int stock,
                              String imageUrl) {
    }

    public record ProductDetail(Long id, String name, String slug, String description, Map<String, Object> attributes,
                                long minPrice, long maxPrice, int totalStock, int soldCount, BigDecimal ratingAvg,
                                int ratingCount, List<CategoryRef> breadcrumb, List<String> images,
                                List<VariantOption> variantOptions, List<VariantView> variants, ShopSummary shop) {
    }

    /** Bộ lọc danh sách; giá theo VND. */
    public record ProductFilter(Long categoryId, Long minPrice, Long maxPrice) {
    }
}
