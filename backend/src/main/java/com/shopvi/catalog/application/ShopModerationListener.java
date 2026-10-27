package com.shopvi.catalog.application;

import com.shopvi.catalog.ProductChanged;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.shop.ShopStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Shop bị khóa / mở khóa: phát {@link ProductChanged} cho từng sản phẩm để chỉ mục tìm kiếm cập nhật. */
@Component
@RequiredArgsConstructor
class ShopModerationListener {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void on(ShopStatusChanged event) {
        if ("BANNED".equals(event.status()) || "ACTIVE".equals(event.status())) {
            productRepository.findIdsByShopId(event.shopId()).forEach(id -> events.publishEvent(new ProductChanged(id)));
        }
    }
}
