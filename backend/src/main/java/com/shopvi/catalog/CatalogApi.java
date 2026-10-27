package com.shopvi.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * API công khai của module catalog cho cart/order.
 */
public interface CatalogApi {

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
}
