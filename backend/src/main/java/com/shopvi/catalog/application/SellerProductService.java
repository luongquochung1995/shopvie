package com.shopvi.catalog.application;

import static com.shopvi.catalog.infra.ProductSpecs.hasStatus;
import static com.shopvi.catalog.infra.ProductSpecs.nameContains;
import static com.shopvi.catalog.infra.ProductSpecs.ofShop;

import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.catalog.application.ProductViews.CategoryRef;
import com.shopvi.catalog.application.ProductViews.VariantView;
import com.shopvi.catalog.application.SellerProductViews.ProductCommand;
import com.shopvi.catalog.application.SellerProductViews.SellerProductDetail;
import com.shopvi.catalog.application.SellerProductViews.SellerProductFilter;
import com.shopvi.catalog.application.SellerProductViews.SellerProductRow;
import com.shopvi.catalog.application.SellerProductViews.VariantCommand;
import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductImage;
import com.shopvi.catalog.domain.ProductVariant;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.catalog.infra.ProductImageRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.catalog.infra.ProductVariantRepository;
import com.shopvi.catalog.infra.ProductVariantRepository.StockSummary;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.common.util.SlugUtils;
import com.shopvi.shop.ShopApi;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Quản lý sản phẩm của seller (P3-2): tạo/sửa sản phẩm kèm phân loại và SKU, ẩn/hiện, xóa mềm.
 * Mọi thao tác chỉ áp dụng cho sản phẩm thuộc shop của chính seller (khác shop → 404 PRODUCT_NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SellerProductService {

    static final int MAX_IMAGES = 9;
    static final int MAX_ATTRIBUTES = 20;
    private static final String SKU_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryQueryService categoryQueryService;
    private final ShopApi shopApi;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<SellerProductRow> list(Long sellerId, SellerProductFilter filter, Pageable pageable) {
        Specification<Product> spec = ofShop(shopApi.requireShopIdOfOwner(sellerId));
        if (filter.status() != null) {
            spec = spec.and(hasStatus(filter.status()));
        }
        if (StringUtils.hasText(filter.keyword())) {
            spec = spec.and(nameContains(filter.keyword().trim()));
        }
        Page<Product> page = productRepository.findAll(spec, pageable);
        List<Long> ids = page.getContent().stream().map(Product::getId).toList();
        Map<Long, StockSummary> stock = ids.isEmpty() ? Map.of() : variantRepository.summarizeStock(ids).stream()
                .collect(Collectors.toMap(StockSummary::getProductId, Function.identity()));
        Map<Long, String> thumbnails = new HashMap<>();
        if (!ids.isEmpty()) {
            imageRepository.findByProductIdInOrderByProductIdAscPositionAscIdAsc(ids)
                    .forEach(img -> thumbnails.putIfAbsent(img.getProductId(), img.getUrl()));
        }
        List<SellerProductRow> rows = page.getContent().stream().map(p -> {
            StockSummary s = stock.get(p.getId());
            return new SellerProductRow(p.getId(), p.getName(), p.getSlug(), thumbnails.get(p.getId()), p.getStatus(),
                    p.getMinPrice(), p.getMaxPrice(), s == null ? 0 : s.getTotalStock().intValue(),
                    s == null ? 0 : s.getVariantCount().intValue(), p.getSoldCount(), p.getRatingAvg(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }).toList();
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SellerProductDetail get(Long sellerId, Long productId) {
        return toDetail(owned(shopApi.requireShopIdOfOwner(sellerId), productId));
    }

    public SellerProductDetail create(Long sellerId, ProductCommand cmd) {
        Long shopId = shopApi.requireActiveShopIdOfOwner(sellerId);
        Normalized n = normalize(cmd);
        Product product = productRepository.save(Product.create(shopId, "tmp-" + UUID.randomUUID(), n.details(),
                cmd.visible() == null || cmd.visible()));
        // Slug duy nhất toàn sàn nhờ hậu tố id, vd. "ao-thun-nam-cotton-1024"
        String base = SlugUtils.slugify(n.details().name());
        product.assignSlug((base.isEmpty() ? "san-pham" : base) + "-" + product.getId());
        saveVariants(shopId, product, n, List.of());
        saveImages(product.getId(), n.images());
        productRepository.flush();
        return toDetail(product);
    }

    /** Sửa toàn bộ thông tin; SKU khớp theo id (có id = sửa, không id = thêm, SKU cũ không gửi lên = xóa). */
    public SellerProductDetail update(Long sellerId, Long productId, ProductCommand cmd) {
        Long shopId = shopApi.requireActiveShopIdOfOwner(sellerId);
        Product product = owned(shopId, productId);
        Normalized n = normalize(cmd);
        product.updateDetails(n.details());
        saveVariants(shopId, product, n, variantRepository.findByProductIdOrderByIdAsc(productId));
        imageRepository.deleteByProductId(productId);
        saveImages(productId, n.images());
        productRepository.flush();
        return toDetail(product);
    }

    /** Ẩn (HIDDEN) / hiện (ACTIVE) sản phẩm; sản phẩm bị khóa (BANNED) không đổi được. */
    public SellerProductDetail setVisible(Long sellerId, Long productId, boolean visible) {
        Product product = owned(shopApi.requireActiveShopIdOfOwner(sellerId), productId);
        product.setVisible(visible);
        productRepository.flush();
        return toDetail(product);
    }

    /** Xóa mềm: sản phẩm biến mất khỏi sàn và giỏ hàng; đơn cũ vẫn giữ snapshot. */
    public void delete(Long sellerId, Long productId) {
        owned(shopApi.requireShopIdOfOwner(sellerId), productId).softDelete(clock.instant());
    }

    private Product owned(Long shopId, Long productId) {
        return productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // ===== Ghi SKU / ảnh

    private void saveVariants(Long shopId, Product product, Normalized n, List<ProductVariant> existing) {
        Map<Long, ProductVariant> byId = existing.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<VariantCommand> commands = n.variants();
        for (int i = 0; i < commands.size(); i++) {
            Long id = commands.get(i).id();
            if (id != null && !byId.containsKey(id)) {
                throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, ErrorCode.VARIANT_NOT_FOUND.getDefaultMessage(),
                        List.of(new ErrorDetail("variants[%d].id".formatted(i), String.valueOf(id))));
            }
        }
        Set<Long> kept = commands.stream().map(VariantCommand::id).filter(id -> id != null).collect(Collectors.toSet());
        List<ProductVariant> removed = existing.stream().filter(v -> !kept.contains(v.getId())).toList();
        if (!removed.isEmpty()) {
            // Xóa trước (và flush) để SKU của dòng bị xóa dùng lại được cho dòng mới trong cùng request
            variantRepository.deleteAll(removed);
            variantRepository.flush();
        }

        List<String> skus = resolveSkus(shopId, product.getId(), commands);
        long min = Long.MAX_VALUE;
        long max = 0;
        List<ProductVariant> created = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            VariantCommand c = commands.get(i);
            Map<String, String> values = n.optionValues().get(i);
            String imageUrl = StringUtils.hasText(c.imageUrl()) ? c.imageUrl().trim() : null;
            if (c.id() == null) {
                created.add(ProductVariant.create(product.getId(), skus.get(i), values, c.price(), c.stock(), imageUrl));
            } else {
                byId.get(c.id()).update(skus.get(i), values, c.price(), c.stock(), imageUrl);
            }
            min = Math.min(min, c.price());
            max = Math.max(max, c.price());
        }
        variantRepository.saveAll(created);
        product.updatePriceRange(min, max);
    }

    /** SKU seller nhập phải không trùng (trong request và với sản phẩm khác); bỏ trống thì tự sinh. */
    private List<String> resolveSkus(Long shopId, Long productId, List<VariantCommand> commands) {
        List<String> skus = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < commands.size(); i++) {
            String sku = StringUtils.hasText(commands.get(i).sku()) ? commands.get(i).sku().trim() : null;
            if (sku != null && !seen.add(sku.toUpperCase(Locale.ROOT))) {
                throw skuConflict(i, sku);
            }
            skus.add(sku);
        }
        List<String> provided = skus.stream().filter(s -> s != null).toList();
        if (!provided.isEmpty()) {
            Map<String, Long> owners = variantRepository.findBySkuIn(provided).stream()
                    .collect(Collectors.toMap(ProductVariant::getSku, ProductVariant::getProductId));
            for (int i = 0; i < skus.size(); i++) {
                Long owner = skus.get(i) == null ? null : owners.get(skus.get(i));
                if (owner != null && !owner.equals(productId)) {
                    throw skuConflict(i, skus.get(i));
                }
            }
        }
        for (int i = 0; i < skus.size(); i++) {
            if (skus.get(i) == null) {
                String generated;
                do {
                    generated = generateSku(shopId);
                } while (!seen.add(generated) || variantRepository.existsBySku(generated));
                skus.set(i, generated);
            }
        }
        return skus;
    }

    /** Vd. "S12-K7Q2M9XA" — tiền tố id shop + 8 ký tự ngẫu nhiên (bỏ ký tự dễ nhầm 0/O, 1/I). */
    static String generateSku(Long shopId) {
        StringBuilder sb = new StringBuilder("S").append(shopId).append('-');
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            sb.append(SKU_ALPHABET.charAt(random.nextInt(SKU_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static BusinessException skuConflict(int index, String sku) {
        return new BusinessException(ErrorCode.SKU_ALREADY_EXISTS, ErrorCode.SKU_ALREADY_EXISTS.getDefaultMessage(),
                List.of(new ErrorDetail("variants[%d].sku".formatted(index), sku)));
    }

    private void saveImages(Long productId, List<String> images) {
        List<ProductImage> rows = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            rows.add(ProductImage.of(productId, images.get(i), i));
        }
        imageRepository.saveAll(rows);
    }

    // ===== Chuẩn hóa / kiểm tra đầu vào

    private Normalized normalize(ProductCommand cmd) {
        requireLeafCategory(cmd.categoryId());
        List<VariantOption> options = VariantMatrix.normalizeOptions(cmd.variantOptions());
        List<Map<String, String>> optionValues = VariantMatrix.normalizeVariants(options, cmd.variants());

        List<String> images = cmd.images() == null ? List.of()
                : cmd.images().stream().map(String::trim).filter(StringUtils::hasText).distinct().toList();
        if (images.isEmpty() || images.size() > MAX_IMAGES) {
            throw invalid("images", "must contain 1 to " + MAX_IMAGES + " images");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (cmd.attributes() != null) {
            cmd.attributes().forEach((k, v) -> {
                if (StringUtils.hasText(k) && StringUtils.hasText(v)) {
                    attributes.put(k.trim(), v.trim());
                }
            });
        }
        if (attributes.size() > MAX_ATTRIBUTES) {
            throw invalid("attributes", "at most " + MAX_ATTRIBUTES + " attributes are allowed");
        }
        String description = StringUtils.hasText(cmd.description()) ? cmd.description().trim() : null;
        return new Normalized(new Product.Details(cmd.categoryId(), cmd.name().trim(), description, attributes, options),
                images, cmd.variants(), optionValues);
    }

    private void requireLeafCategory(Long categoryId) {
        CategoryNode node = categoryQueryService.getTree().find(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        if (!node.children().isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_LEAF);
        }
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                List.of(new ErrorDetail(field, message)));
    }

    private SellerProductDetail toDetail(Product p) {
        List<VariantView> variants = variantRepository.findByProductIdOrderByIdAsc(p.getId()).stream()
                .map(v -> new VariantView(v.getId(), v.getSku(), v.getOptionValues(), v.getPrice(), v.getStock(),
                        v.getImageUrl()))
                .toList();
        List<String> images = imageRepository.findByProductIdOrderByPositionAscIdAsc(p.getId()).stream()
                .map(ProductImage::getUrl).toList();
        List<CategoryRef> path = categoryQueryService.getTree().breadcrumb(p.getCategoryId()).stream()
                .map(c -> new CategoryRef(c.id(), c.name(), c.slug())).toList();
        return new SellerProductDetail(p.getId(), p.getName(), p.getSlug(), p.getStatus(), p.getCategoryId(), path,
                p.getDescription(), p.getAttributes(), images, p.getVariantOptions(), variants, p.getMinPrice(),
                p.getMaxPrice(), variants.stream().mapToInt(VariantView::stock).sum(), p.getSoldCount(),
                p.getRatingAvg(), p.getRatingCount(), p.getCreatedAt(), p.getUpdatedAt());
    }

    /** Đầu vào đã chuẩn hóa; {@code optionValues.get(i)} là giá trị phân loại của {@code variants.get(i)}. */
    private record Normalized(Product.Details details, List<String> images, List<VariantCommand> variants,
                              List<Map<String, String>> optionValues) {
    }
}
