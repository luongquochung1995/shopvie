package com.shopvi.promotion;

import java.util.UUID;

/**
 * Bản tin Kafka (topic {@value #TOPIC}): người mua đã giữ được quota flash sale trên Redis, cần tạo đơn.
 * Đơn được tạo bất đồng bộ; {@code reservationId} dùng làm checkout id nên xử lý lặp không tạo đơn trùng.
 *
 * @param salePrice giá flash sale (VND) tại lúc giữ hàng
 */
public record FlashSaleOrderRequested(UUID reservationId, Long itemId, Long variantId, Long userId, int quantity,
                                      long salePrice, Long addressId) {

    public static final String TOPIC = "shopvi.flash-sale-orders";
}
