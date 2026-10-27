package com.shopvi.catalog.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.ProductChanged;
import com.shopvi.catalog.SearchDocument;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.domain.ProductVariant;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.catalog.infra.ProductImageRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.catalog.infra.ProductVariantRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogApiService implements CatalogApi {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ShopApi shopApi;
    private final CategoryQueryService categoryQueryService;
    private final ApplicationEventPublisher events;

    @Override
    public Optional<VariantSnapshot> findVariant(Long variantId) {
        return Optional.ofNullable(findVariants(List.of(variantId)).get(variantId));
    }

    @Override
    public Map<Long, VariantSnapshot> findVariants(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        List<ProductVariant> variants = variantRepository.findByIdIn(variantIds);
        // Sản phẩm đã xóa mềm không được trả về (@SQLRestriction) → SKU đó bị coi như không tồn tại
        Map<Long, Product> products = productRepository.findAllById(
                        variants.stream().map(ProductVariant::getProductId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Set<Long> activeShops = shopApi.activeShopIds(
                products.values().stream().map(Product::getShopId).collect(Collectors.toSet()));
        Map<Long, String> thumbnails = new HashMap<>();
        imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(products.keySet())
                .forEach(img -> thumbnails.putIfAbsent(img.getProductId(), img.getUrl()));

        Map<Long, VariantSnapshot> result = new HashMap<>();
        for (ProductVariant v : variants) {
            Product p = products.get(v.getProductId());
            if (p == null) {
                continue;
            }
            boolean purchasable = p.getStatus() == ProductStatus.ACTIVE && activeShops.contains(p.getShopId());
            result.put(v.getId(), new VariantSnapshot(v.getId(), v.getSku(), p.getId(), p.getName(), p.getSlug(),
                    p.getShopId(), variantName(p.getVariantOptions(), v.getOptionValues()),
                    v.getImageUrl() != null ? v.getImageUrl() : thumbnails.get(p.getId()),
                    v.getPrice(), v.getStock(), purchasable));
        }
        return result;
    }

    @Override
    @Transactional
    public void reserveStock(Long variantId, int quantity) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND));
        if (variantRepository.decreaseStock(variantId, quantity) == 0) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, ErrorCode.OUT_OF_STOCK.getDefaultMessage(),
                    List.of(new ErrorDetail("variantId", String.valueOf(variantId))));
        }
        productRepository.addSoldCount(variant.getProductId(), quantity);
        events.publishEvent(new ProductChanged(variant.getProductId()));
    }

    @Override
    @Transactional
    public void releaseStock(Long variantId, int quantity) {
        variantRepository.findById(variantId).ifPresent(variant -> {
            variantRepository.increaseStock(variantId, quantity);
            productRepository.addSoldCount(variant.getProductId(), -quantity);
            events.publishEvent(new ProductChanged(variant.getProductId()));
        });
    }

    @Override
    public Optional<Long> findActiveProductId(String slug) {
        return productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE).map(Product::getId);
    }

    @Override
    @Transactional
    public void updateRating(Long productId, BigDecimal ratingAvg, int ratingCount) {
        productRepository.updateRating(productId, ratingAvg, ratingCount);
        events.publishEvent(new ProductChanged(productId));
    }

    @Override
    public Map<String, Long> countProductsByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ProductStatus s : ProductStatus.values()) {
            counts.put(s.name(), 0L);
        }
        productRepository.countGroupByStatus().forEach(r -> counts.put(((ProductStatus) r[0]).name(), (Long) r[1]));
        return counts;
    }

    @Override
    public List<SearchDocument> findSearchDocuments(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findAllById(productIds).stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .toList();
        Set<Long> shopIds = products.stream().map(Product::getShopId).collect(Collectors.toSet());
        Set<Long> activeShops = shopApi.activeShopIds(shopIds);
        Map<Long, ShopSummary> shops = shopApi.findSummaries(shopIds);
        Map<Long, String> thumbnails = new HashMap<>();
        imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(
                        products.stream().map(Product::getId).toList())
                .forEach(img -> thumbnails.putIfAbsent(img.getProductId(), img.getUrl()));
        CategoryTree tree = categoryQueryService.getTree();
        return products.stream()
                .filter(p -> activeShops.contains(p.getShopId()))
                .map(p -> {
                    List<CategoryTree.CategoryNode> path = tree.breadcrumb(p.getCategoryId());
                    ShopSummary shop = shops.get(p.getShopId());
                    return new SearchDocument(p.getId(), p.getName(), p.getSlug(),
                            path.stream().map(CategoryTree.CategoryNode::id).toList(),
                            path.stream().map(CategoryTree.CategoryNode::name).toList(),
                            p.getShopId(), shop == null ? null : shop.name(), keywords(p), thumbnails.get(p.getId()),
                            p.getMinPrice(), p.getMaxPrice(), p.getSoldCount(), p.getRatingAvg(), p.getRatingCount(),
                            p.getCreatedAt());
                })
                .toList();
    }

    @Override
    public List<Long> activeProductIdsAfter(long afterId, int limit) {
        return productRepository.findActiveIdsAfter(afterId, PageRequest.of(0, limit));
    }

    /** Giá trị thuộc tính và phân loại, vd. "Coolmate Cotton 100% Đỏ Xanh M L". */
    static String keywords(Product p) {
        StringBuilder sb = new StringBuilder();
        if (p.getAttributes() != null) {
            p.getAttributes().values().forEach(v -> sb.append(v).append(' '));
        }
        if (p.getVariantOptions() != null) {
            p.getVariantOptions().forEach(o -> o.values().forEach(v -> sb.append(v).append(' ')));
        }
        return sb.toString().trim();
    }

    /** "Đỏ, M" theo thứ tự nhóm phân loại của sản phẩm. */
    static String variantName(List<VariantOption> options, Map<String, String> values) {
        if (options == null || options.isEmpty() || values == null || values.isEmpty()) {
            return null;
        }
        return options.stream().map(o -> values.get(o.name())).filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
}
