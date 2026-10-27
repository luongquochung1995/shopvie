package com.shopvi.order.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hoàn kho các món của đơn bị hủy (người mua hoặc seller hủy) — chạy trong transaction của nơi gọi. */
@Component
@RequiredArgsConstructor
class OrderStockReleaser {

    private final CatalogApi catalogApi;

    void release(Order order) {
        // Theo thứ tự variantId như lúc trừ kho để các transaction khóa dòng cùng thứ tự (tránh deadlock)
        order.getItems().stream()
                .filter(i -> i.getVariantId() != null)
                .sorted(Comparator.comparing(OrderItem::getVariantId))
                .forEach(i -> catalogApi.releaseStock(i.getVariantId(), i.getQuantity()));
    }
}
