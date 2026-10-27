package com.shopvi.catalog;

/**
 * Thông tin một SKU tại thời điểm đọc, dùng cho giỏ hàng và snapshot đơn hàng.
 *
 * @param variantName giá trị phân loại theo thứ tự nhóm, vd. "Đỏ, M" (null nếu sản phẩm không có phân loại)
 * @param imageUrl    ảnh của SKU, không có thì ảnh đầu tiên của sản phẩm
 * @param purchasable sản phẩm đang bán (ACTIVE, chưa xóa)
 */
public record VariantSnapshot(Long variantId, String sku, Long productId, String productName, String productSlug,
                              Long shopId, String variantName, String imageUrl, long price, int stock,
                              boolean purchasable) {
}
