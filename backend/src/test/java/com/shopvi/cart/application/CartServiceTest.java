package com.shopvi.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.cart.domain.CartItem;
import com.shopvi.cart.infra.CartItemRepository;
import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CartServiceTest {

    private final CartItemRepository repository = mock(CartItemRepository.class);
    private final CatalogApi catalog = mock(CatalogApi.class);
    private final ShopApi shops = mock(ShopApi.class);
    private final CartService service = new CartService(repository, catalog, shops);

    @BeforeEach
    void setUp() {
        when(catalog.findVariant(1L)).thenReturn(Optional.of(variant(1L, 5, true)));
        when(catalog.findVariant(2L)).thenReturn(Optional.of(variant(2L, 5, false)));
        when(catalog.findVariants(any())).thenReturn(Map.of(1L, variant(1L, 5, true)));
        when(shops.findSummaries(any())).thenReturn(Map.of(1L, new ShopSummary(1L, "S", "s", null, BigDecimal.ONE)));
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());
    }

    @Test
    void addingExistingVariantAccumulatesQuantityAndReselects() {
        CartItem existing = CartItem.create(7L, 1L, 2);
        existing.select(false);
        when(repository.findByUserIdAndVariantId(7L, 1L)).thenReturn(Optional.of(existing));

        service.add(7L, 1L, 3);

        assertThat(existing.getQuantity()).isEqualTo(5);
        assertThat(existing.isSelected()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void cannotExceedStockAndReportsHowManyCanStillBeAdded() {
        when(repository.findByUserIdAndVariantId(7L, 1L)).thenReturn(Optional.of(CartItem.create(7L, 1L, 4)));

        assertThatThrownBy(() -> service.add(7L, 1L, 2))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
                    assertThat(e.getDetails().getFirst().message()).isEqualTo("1");
                });
    }

    @Test
    void cannotAddUnavailableProduct() {
        assertThatThrownBy(() -> service.add(7L, 2L, 1))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE);
    }

    @Test
    void cartSizeIsLimited() {
        when(repository.findByUserIdAndVariantId(7L, 1L)).thenReturn(Optional.empty());
        when(repository.countByUserId(7L)).thenReturn((long) CartService.MAX_ITEMS);

        assertThatThrownBy(() -> service.add(7L, 1L, 1))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CART_LIMIT_REACHED);
    }

    @Test
    void viewComputesSelectedTotalsOnlyForAvailableSelectedLines() {
        CartItem ok = item(10L, 1L, 2, true);
        CartItem tooMany = item(11L, 3L, 9, true);
        CartItem unselected = item(12L, 4L, 1, false);
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(ok, tooMany, unselected));
        when(catalog.findVariants(any())).thenReturn(Map.of(
                1L, variant(1L, 5, true), 3L, variant(3L, 5, true), 4L, variant(4L, 5, true)));

        CartView view = service.view(7L);

        assertThat(view.itemCount()).isEqualTo(3);
        assertThat(view.selectedQuantity()).isEqualTo(2);
        assertThat(view.selectedSubtotal()).isEqualTo(200_000);
        assertThat(view.shops().getFirst().items()).extracting(CartView.Item::available).containsExactly(true, false, true);
    }

    @Test
    void decreasingQuantityIsAllowedEvenAboveStock() {
        CartItem item = item(10L, 1L, 9, true);
        when(repository.findByIdAndUserId(10L, 7L)).thenReturn(Optional.of(item));

        service.update(7L, 10L, 6, null);

        assertThat(item.getQuantity()).isEqualTo(6);
    }

    @Test
    void itemOfAnotherUserIsNotFound() {
        when(repository.findByIdAndUserId(10L, 8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(8L, 10L, 1, null))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    private static CartItem item(Long id, Long variantId, int quantity, boolean selected) {
        CartItem item = CartItem.create(7L, variantId, quantity);
        item.select(selected);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static VariantSnapshot variant(Long id, int stock, boolean purchasable) {
        return new VariantSnapshot(id, "SKU" + id, 1L, "P", "p", 1L, null, null, 100_000, stock, purchasable);
    }
}
