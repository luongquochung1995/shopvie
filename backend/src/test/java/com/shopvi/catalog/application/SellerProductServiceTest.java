package com.shopvi.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.catalog.application.SellerProductViews.ProductCommand;
import com.shopvi.catalog.application.SellerProductViews.VariantCommand;
import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.domain.ProductVariant;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.catalog.infra.ProductImageRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.catalog.infra.ProductVariantRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.ShopApi;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SellerProductServiceTest {

    private static final Long SELLER = 5L;
    private static final Long SHOP = 9L;
    private static final Long LEAF = 2L;
    private static final List<VariantOption> COLORS = List.of(new VariantOption("Màu", List.of("Đỏ", "Xanh")));

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final CategoryQueryService categories = mock(CategoryQueryService.class);
    private final ShopApi shops = mock(ShopApi.class);
    private final SellerProductService service = new SellerProductService(products, variants, images, categories,
            shops, Clock.systemUTC());

    @BeforeEach
    void setUp() {
        CategoryNode leaf = new CategoryNode(LEAF, 1L, "Áo thun", "ao-thun", 1, 0, null, List.of());
        when(categories.getTree()).thenReturn(new CategoryTree(List.of(
                new CategoryNode(1L, null, "Thời trang", "thoi-trang", 0, 0, null, List.of(leaf)))));
        when(shops.requireActiveShopIdOfOwner(SELLER)).thenReturn(SHOP);
        when(shops.requireShopIdOfOwner(SELLER)).thenReturn(SHOP);
        when(products.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 42L));
        when(variants.findBySkuIn(any())).thenReturn(List.of());
    }

    @Test
    void createAssignsSlugWithIdPriceRangeAndGeneratesMissingSkus() {
        var detail = service.create(SELLER, command(List.of(
                new VariantCommand(null, Map.of("Màu", "Đỏ"), 120_000, 5, "AO-DO", null),
                new VariantCommand(null, Map.of("Màu", "Xanh"), 99_000, 0, null, null)), null));

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(products).save(product.capture());
        assertThat(product.getValue().getSlug()).isEqualTo("ao-thun-cotton-42");
        assertThat(product.getValue().getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getValue().getMinPrice()).isEqualTo(99_000);
        assertThat(product.getValue().getMaxPrice()).isEqualTo(120_000);
        assertThat(product.getValue().getAttributes()).containsEntry("Chất liệu", "Cotton").doesNotContainKey(" ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductVariant>> saved = ArgumentCaptor.forClass(List.class);
        verify(variants).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ProductVariant::getSku)
                .first().isEqualTo("AO-DO");
        assertThat(saved.getValue().get(1).getSku()).matches("S9-[A-Z2-9]{8}");
        assertThat(saved.getValue()).allSatisfy(v -> assertThat(v.getProductId()).isEqualTo(42L));
        verify(images).saveAll(any());
        assertThat(detail.categoryPath()).extracting("id").containsExactly(1L, LEAF);
    }

    @Test
    void createHiddenWhenNotVisible() {
        service.create(SELLER, command(List.of(
                new VariantCommand(null, Map.of("Màu", "Đỏ"), 120_000, 5, null, null),
                new VariantCommand(null, Map.of("Màu", "Xanh"), 99_000, 0, null, null)), false));

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(products).save(product.capture());
        assertThat(product.getValue().getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void createRequiresLeafCategory() {
        ProductCommand cmd = new ProductCommand(1L, "Áo thun cotton", null, Map.of(), List.of("https://img/1.jpg"),
                List.of(), List.of(new VariantCommand(null, Map.of(), 100_000, 1, null, null)), true);

        assertError(() -> service.create(SELLER, cmd), ErrorCode.CATEGORY_NOT_LEAF);
        verify(products, never()).save(any());
    }

    @Test
    void createRejectsSkuOfAnotherProduct() {
        ProductVariant taken = withId(ProductVariant.create(77L, "AO-DO", Map.of(), 1_000, 1, null), 500L);
        when(variants.findBySkuIn(any())).thenReturn(List.of(taken));

        assertThatThrownBy(() -> service.create(SELLER, command(List.of(
                new VariantCommand(null, Map.of("Màu", "Đỏ"), 120_000, 5, "AO-DO", null),
                new VariantCommand(null, Map.of("Màu", "Xanh"), 99_000, 0, null, null)), true)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SKU_ALREADY_EXISTS);
                    assertThat(ex.getDetails()).singleElement().extracting("field").isEqualTo("variants[0].sku");
                });
    }

    @Test
    void createRejectsDuplicateSkusInRequest() {
        assertError(() -> service.create(SELLER, command(List.of(
                new VariantCommand(null, Map.of("Màu", "Đỏ"), 120_000, 5, "AO-1", null),
                new VariantCommand(null, Map.of("Màu", "Xanh"), 99_000, 0, "ao-1", null)), true)),
                ErrorCode.SKU_ALREADY_EXISTS);
    }

    @Test
    void createRequiresActiveShop() {
        when(shops.requireActiveShopIdOfOwner(SELLER)).thenThrow(new BusinessException(ErrorCode.SHOP_NOT_ACTIVE));

        assertError(() -> service.create(SELLER, command(List.of(), true)), ErrorCode.SHOP_NOT_ACTIVE);
    }

    @Test
    void updateOfAnotherShopsProductIsNotFound() {
        when(products.findByIdAndShopId(42L, SHOP)).thenReturn(Optional.empty());

        assertError(() -> service.update(SELLER, 42L, command(List.of(), true)), ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void updateMatchesVariantsByIdAddsNewAndDeletesMissing() {
        Product product = existingProduct();
        ProductVariant red = withId(ProductVariant.create(42L, "AO-DO", Map.of("Màu", "Đỏ"), 100_000, 3, null), 1L);
        ProductVariant blue = withId(ProductVariant.create(42L, "AO-XANH", Map.of("Màu", "Xanh"), 100_000, 3, null), 2L);
        when(products.findByIdAndShopId(42L, SHOP)).thenReturn(Optional.of(product));
        when(variants.findByProductIdOrderByIdAsc(42L)).thenReturn(List.of(red, blue));
        when(variants.findBySkuIn(any())).thenReturn(List.of(red));

        // Đổi nhóm "Màu" thành Đỏ + Vàng: giữ SKU đỏ (sửa giá/kho), thêm SKU vàng, xóa SKU xanh
        service.update(SELLER, 42L, new ProductCommand(LEAF, "Áo thun cotton mới", "Mô tả", Map.of(),
                List.of("https://img/2.jpg"), List.of(new VariantOption("Màu", List.of("Đỏ", "Vàng"))), List.of(
                new VariantCommand(1L, Map.of("Màu", "Đỏ"), 150_000, 10, "AO-DO", null),
                new VariantCommand(null, Map.of("Màu", "Vàng"), 90_000, 4, null, null)), null));

        verify(variants).deleteAll(List.of(blue));
        assertThat(red.getPrice()).isEqualTo(150_000);
        assertThat(red.getStock()).isEqualTo(10);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductVariant>> saved = ArgumentCaptor.forClass(List.class);
        verify(variants).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(v -> {
            assertThat(v.getOptionValues()).containsEntry("Màu", "Vàng");
            assertThat(v.getStock()).isEqualTo(4);
        });
        assertThat(product.getName()).isEqualTo("Áo thun cotton mới");
        assertThat(product.getSlug()).isEqualTo("ao-thun-cu-42");
        assertThat(product.getMinPrice()).isEqualTo(90_000);
        assertThat(product.getMaxPrice()).isEqualTo(150_000);
        verify(images).deleteByProductId(42L);
    }

    @Test
    void updateRejectsVariantIdOfAnotherProduct() {
        when(products.findByIdAndShopId(42L, SHOP)).thenReturn(Optional.of(existingProduct()));
        when(variants.findByProductIdOrderByIdAsc(42L)).thenReturn(List.of());

        assertError(() -> service.update(SELLER, 42L, new ProductCommand(LEAF, "Áo thun cotton", null, Map.of(),
                List.of("https://img/1.jpg"), List.of(),
                List.of(new VariantCommand(999L, Map.of(), 100_000, 1, null, null)), null)), ErrorCode.VARIANT_NOT_FOUND);
    }

    @Test
    void bannedProductCannotBeShownAgain() {
        Product product = existingProduct();
        ReflectionTestUtils.setField(product, "status", ProductStatus.BANNED);
        when(products.findByIdAndShopId(42L, SHOP)).thenReturn(Optional.of(product));

        assertError(() -> service.setVisible(SELLER, 42L, true), ErrorCode.PRODUCT_BANNED);
    }

    @Test
    void generatedSkuUsesShopPrefixAndUnambiguousCharacters() {
        assertThat(SellerProductService.generateSku(12L)).matches("S12-[A-HJ-NP-Z2-9]{8}");
    }

    private static ProductCommand command(List<VariantCommand> variantCommands, Boolean visible) {
        return new ProductCommand(LEAF, "  Áo thun cotton ", "Áo đẹp", Map.of("Chất liệu", " Cotton ", " ", "x"),
                List.of("https://img/1.jpg", "https://img/1.jpg", "https://img/2.jpg"), COLORS, variantCommands, visible);
    }

    private static Product existingProduct() {
        Product product = Product.create(SHOP, "ao-thun-cu-42", new Product.Details(LEAF, "Áo thun cũ", null,
                Map.of(), COLORS), true);
        return withId(product, 42L);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
}
