package com.shopvi.search.application;

import java.util.List;

/** Dữ liệu vào/ra của tìm kiếm sản phẩm. Tiền tệ: VND. */
public final class SearchViews {

    private SearchViews() {
    }

    public enum SortBy { RELEVANCE, BEST_SELLING, NEWEST, PRICE_ASC, PRICE_DESC }

    /** @param keyword từ khóa (có dấu hoặc không dấu); trống = duyệt toàn bộ */
    public record SearchQuery(String keyword, Long categoryId, Long minPrice, Long maxPrice, SortBy sort, int page,
                              int size) {
    }

    /**
     * @param highlightedName tên sản phẩm với phần khớp bọc trong {@code <mark>} (đã escape HTML); null nếu không khớp tên
     */
    public record SearchHit(Long id, String name, String highlightedName, String slug, String thumbnailUrl,
                            long minPrice, long maxPrice, int soldCount, double ratingAvg, int ratingCount,
                            String shopName) {
    }

    public record SearchResult(List<SearchHit> items, int page, int size, long totalItems, int totalPages) {
    }

    public record Suggestion(String name, String slug) {
    }
}
