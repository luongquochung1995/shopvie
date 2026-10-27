package com.shopvi.catalog.application;

import static com.shopvi.catalog.infra.ProductSpecs.hasStatus;
import static com.shopvi.catalog.infra.ProductSpecs.nameContains;
import static com.shopvi.catalog.infra.ProductSpecs.ofShop;

import com.shopvi.catalog.ProductChanged;
import com.shopvi.catalog.ProductModerated;
import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.infra.ProductImageRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Admin hậu kiểm sản phẩm: rà soát theo trạng thái/shop/tên, khóa (kèm lý do) và mở khóa. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ShopApi shopApi;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<AdminProduct> list(ProductStatus status, Long shopId, String keyword, Pageable pageable) {
        Specification<Product> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        if (shopId != null) {
            spec = spec.and(ofShop(shopId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(nameContains(keyword.trim()));
        }
        Page<Product> page = productRepository.findAll(spec, pageable);
        Map<Long, ShopSummary> shops = shopApi.findSummaries(page.getContent().stream().map(Product::getShopId).toList());
        Map<Long, String> thumbnails = new HashMap<>();
        if (!page.isEmpty()) {
            imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(
                            page.getContent().stream().map(Product::getId).toList())
                    .forEach(img -> thumbnails.putIfAbsent(img.getProductId(), img.getUrl()));
        }
        List<AdminProduct> items = page.getContent().stream()
                .map(p -> AdminProduct.of(p, thumbnails.get(p.getId()), shops.get(p.getShopId())))
                .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    public AdminProduct ban(Long productId, String reason) {
        Product p = get(productId);
        p.ban(reason.trim());
        return moderated(p, true);
    }

    public AdminProduct unban(Long productId) {
        Product p = get(productId);
        p.unban();
        return moderated(p, false);
    }

    private AdminProduct moderated(Product p, boolean banned) {
        productRepository.flush();
        events.publishEvent(new ProductModerated(p.getId(), p.getShopId(), p.getName(), banned, p.getBanReason()));
        events.publishEvent(new ProductChanged(p.getId()));
        String thumbnail = imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(List.of(p.getId()))
                .stream().findFirst().map(img -> img.getUrl()).orElse(null);
        return AdminProduct.of(p, thumbnail, shopApi.findSummaries(List.of(p.getShopId())).get(p.getShopId()));
    }

    private Product get(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    public record AdminProduct(Long id, String name, String slug, String thumbnailUrl, ProductStatus status,
                               String banReason, ShopSummary shop, long minPrice, long maxPrice, int soldCount,
                               BigDecimal ratingAvg, Instant createdAt, Instant updatedAt) {
        static AdminProduct of(Product p, String thumbnail, ShopSummary shop) {
            return new AdminProduct(p.getId(), p.getName(), p.getSlug(), thumbnail, p.getStatus(), p.getBanReason(), shop,
                    p.getMinPrice(), p.getMaxPrice(), p.getSoldCount(), p.getRatingAvg(), p.getCreatedAt(),
                    p.getUpdatedAt());
        }
    }
}
