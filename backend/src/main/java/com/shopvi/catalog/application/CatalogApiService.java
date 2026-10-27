package com.shopvi.catalog.application;

import com.shopvi.catalog.CatalogApi;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
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
    }

    @Override
    @Transactional
    public void releaseStock(Long variantId, int quantity) {
        variantRepository.findById(variantId).ifPresent(variant -> {
            variantRepository.increaseStock(variantId, quantity);
            productRepository.addSoldCount(variant.getProductId(), -quantity);
        });
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
