package com.shopvi.cart.application;

import com.shopvi.shop.ShopSummary;
import java.util.List;

/**
 * Giỏ hàng nhóm theo shop.
 *
 * @param itemCount        số dòng (SKU khác nhau) trong giỏ — hiển thị ở badge
 * @param selectedQuantity tổng số lượng của các dòng đã chọn và còn mua được
 * @param selectedSubtotal tổng tiền các dòng đã chọn và còn mua được (VND)
 */
public record CartView(List<ShopGroup> shops, int itemCount, int selectedQuantity, long selectedSubtotal) {

    public record ShopGroup(ShopSummary shop, List<Item> items) {
    }

    /**
     * @param available còn bán và còn hàng; dòng không available không được tính tiền / không đặt được
     */
    public record Item(Long id, Long variantId, Long productId, String productName, String productSlug,
                       String variantName, String imageUrl, long unitPrice, int quantity, int stock, boolean selected,
                       boolean available, long lineTotal) {
    }
}
