package com.shopvi.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API công khai của module catalog cho cart/order.
 */
public interface CatalogApi {

    /** Số sản phẩm (chưa xóa) theo trạng thái — thống kê admin. */
    Map<String, Long> countProductsByStatus();

    Optional<VariantSnapshot> findVariant(Long variantId);

    /** SKU không tồn tại sẽ không có trong map. */
    Map<Long, VariantSnapshot> findVariants(Collection<Long> variantIds);

    /**
     * Trừ kho nguyên tử ({@code UPDATE ... WHERE stock >= :q}) và tăng số đã bán.
     * Chạy trong transaction của nơi gọi. Ném {@code OUT_OF_STOCK} nếu không đủ hàng.
     */
    void reserveStock(Long variantId, int quantity);

    /** Hoàn kho khi hủy đơn (giảm số đã bán tương ứng). */
    void releaseStock(Long variantId, int quantity);

    /** Id sản phẩm đang bán theo slug (empty nếu không có / không bán). */
    Optional<Long> findActiveProductId(String slug);

    /** Dữ liệu chỉ mục tìm kiếm của các sản phẩm đang bán (sản phẩm ẩn/xóa/shop ngừng bán không có trong kết quả). */
    List<SearchDocument> findSearchDocuments(Collection<Long> productIds);

    /** Id sản phẩm đang bán lớn hơn {@code afterId}, tăng dần (duyệt toàn bộ để reindex). */
    List<Long> activeProductIdsAfter(long afterId, int limit);

    /** Cập nhật điểm đánh giá trung bình (1 chữ số thập phân) và số lượt đánh giá của sản phẩm. */
    void updateRating(Long productId, BigDecimal ratingAvg, int ratingCount);
}
