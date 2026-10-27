package com.shopvi.cart;

import java.util.Collection;
import java.util.List;

/**
 * API công khai của module cart cho checkout.
 */
public interface CartApi {

    /** Các dòng đang được chọn (selected = true) của user. */
    List<CartLine> selectedLines(Long userId);

    /** Xóa các SKU đã đặt hàng khỏi giỏ (trong transaction của checkout). */
    void removeVariants(Long userId, Collection<Long> variantIds);
}
