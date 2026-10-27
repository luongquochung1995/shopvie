package com.shopvi.catalog.application;

import static com.shopvi.catalog.infra.ProductSpecs.hasStatus;
import static com.shopvi.catalog.infra.ProductSpecs.inCategories;
import static com.shopvi.catalog.infra.ProductSpecs.minPriceAtLeast;
import static com.shopvi.catalog.infra.ProductSpecs.minPriceAtMost;
import static com.shopvi.catalog.infra.ProductSpecs.shopNotIn;

import com.shopvi.catalog.application.ProductViews.CategoryRef;
import com.shopvi.catalog.application.ProductViews.ProductCard;
import com.shopvi.catalog.application.ProductViews.ProductDetail;
import com.shopvi.catalog.application.ProductViews.ProductFilter;
import com.shopvi.catalog.application.ProductViews.VariantView;
import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductImage;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.domain.ProductVariant;
import com.shopvi.catalog.infra.ProductImageRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.catalog.infra.ProductVariantRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.web.PageResponse;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    public static final String DETAIL_CACHE = "product-detail";
    public static final String LIST_CACHE = "product-list";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryQueryService categoryQueryService;
    private final ShopApi shopApi;

    /** Trang danh sách công khai (cache Redis 1 phút theo bộ lọc + trang + sắp xếp; trang chủ và trang danh mục). */
    @Cacheable(cacheNames = LIST_CACHE, key = "#filter.categoryId() + ':' + #filter.minPrice() + ':' + #filter.maxPrice() + ':'"
            + " + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort")
    public PageResponse<ProductCard> listPage(ProductFilter filter, Pageable pageable) {
        return PageResponse.from(search(filter, pageable));
    }

    /** Chỉ trả sản phẩm ACTIVE của shop không bị khóa; lọc theo danh mục bao gồm cả danh mục con. */
    public Page<ProductCard> search(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = hasStatus(ProductStatus.ACTIVE);
        Set<Long> bannedShops = shopApi.bannedShopIds();
        if (!bannedShops.isEmpty()) {
            spec = spec.and(shopNotIn(bannedShops));
        }
        if (filter.categoryId() != null) {
            List<Long> categoryIds = categoryQueryService.getTree().selfAndDescendantIds(filter.categoryId());
            if (categoryIds.isEmpty()) {
                throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
            }
            spec = spec.and(inCategories(categoryIds));
        }
        if (filter.minPrice() != null) {
            spec = spec.and(minPriceAtLeast(filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            spec = spec.and(minPriceAtMost(filter.maxPrice()));
        }
        Page<Product> page = productRepository.findAll(spec, pageable);
        Map<Long, String> thumbnails = thumbnails(page.getContent());
        List<ProductCard> cards = page.getContent().stream()
                .map(p -> new ProductCard(p.getId(), p.getName(), p.getSlug(), thumbnails.get(p.getId()),
                        p.getMinPrice(), p.getMaxPrice(), p.getSoldCount(), p.getRatingAvg(), p.getRatingCount(),
                        p.getCreatedAt()))
                .toList();
        return new PageImpl<>(cards, pageable, page.getTotalElements());
    }

    /** Chi tiết sản phẩm (cache Redis theo slug, evict khi sản phẩm thay đổi — xem {@code ProductCacheEvictor}). */
    @Cacheable(cacheNames = DETAIL_CACHE, key = "#slug")
    public ProductDetail getDetail(String slug) {
        Product p = productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        // Sản phẩm của shop chưa ACTIVE/bị khóa không hiển thị
        ShopSummary shop = shopApi.findActiveSummary(p.getShopId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        List<VariantView> variants = variantRepository.findByProductIdOrderByIdAsc(p.getId()).stream()
                .map(ProductQueryService::toView)
                .toList();
        List<String> images = imageRepository.findByProductIdOrderByPositionAscIdAsc(p.getId()).stream()
                .map(ProductImage::getUrl)
                .toList();
        List<CategoryRef> breadcrumb = categoryQueryService.getTree().breadcrumb(p.getCategoryId()).stream()
                .map(n -> new CategoryRef(n.id(), n.name(), n.slug()))
                .toList();
        int totalStock = variants.stream().mapToInt(VariantView::stock).sum();
        return new ProductDetail(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getAttributes(),
                p.getMinPrice(), p.getMaxPrice(), totalStock, p.getSoldCount(), p.getRatingAvg(), p.getRatingCount(),
                breadcrumb, images, p.getVariantOptions(), variants, shop);
    }

    /** Ảnh đầu tiên (position nhỏ nhất) của mỗi sản phẩm — 1 query cho cả trang. */
    private Map<Long, String> thumbnails(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(
                        products.stream().map(Product::getId).toList())
                .forEach(img -> result.putIfAbsent(img.getProductId(), img.getUrl()));
        return result;
    }

    private static VariantView toView(ProductVariant v) {
        return new VariantView(v.getId(), v.getSku(), v.getOptionValues(), v.getPrice(), v.getStock(), v.getImageUrl());
    }
}
