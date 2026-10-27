package com.shopvi.order.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import com.shopvi.promotion.PromotionApi;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hoàn kho và trả lượt voucher của đơn bị hủy (người mua / seller / quá hạn thanh toán) — trong transaction của nơi gọi. */
@Component
@RequiredArgsConstructor
class OrderStockReleaser {

    private final CatalogApi catalogApi;
    private final PromotionApi promotionApi;

    void release(Order order) {
        // Theo thứ tự variantId như lúc trừ kho để các transaction khóa dòng cùng thứ tự (tránh deadlock)
        order.getItems().stream()
                .filter(i -> i.getVariantId() != null)
                .sorted(Comparator.comparing(OrderItem::getVariantId))
                .forEach(i -> catalogApi.releaseStock(i.getVariantId(), i.getQuantity()));
        promotionApi.releaseVouchers(order.getId());
    }
}
