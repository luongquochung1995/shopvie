package com.shopvi.catalog.application;

import com.shopvi.catalog.ProductChanged;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.shop.ShopStatusChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Xóa cache sản phẩm ngay sau khi thay đổi được commit (đồng bộ, trước khi trả response): chi tiết theo slug khi
 * sản phẩm đổi; toàn bộ trang danh sách khi shop bị khóa / mở khóa.
 */
@Component
@RequiredArgsConstructor
class ProductCacheEvictor {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(ProductChanged event) {
        Cache detail = cacheManager.getCache(ProductQueryService.DETAIL_CACHE);
        if (detail != null) {
            productRepository.findSlugIncludingDeleted(event.productId()).ifPresent(detail::evict);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void on(ShopStatusChanged event) {
        Cache list = cacheManager.getCache(ProductQueryService.LIST_CACHE);
        if (list != null) {
            list.clear();
        }
    }
}
