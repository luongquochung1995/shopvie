package com.shopvi.shop.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.application.ShopViews.ShopInfoCommand;
import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import com.shopvi.shop.infra.ShopRepository;
import com.shopvi.user.UserApi;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SellerShopServiceTest {

    private final ShopRepository shops = mock(ShopRepository.class);
    private final UserApi users = mock(UserApi.class);
    private final SellerShopService service = new SellerShopService(shops, users);

    @BeforeEach
    void setUp() {
        when(shops.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shops.findByOwnerId(7L)).thenReturn(Optional.empty());
    }

    @Test
    void registerCreatesPendingShopAndPromotesUser() {
        var shop = service.register(7L, new ShopInfoCommand("  Shop Áo Đẹp ", " ", "https://img/logo.png"));

        assertThat(shop.status()).isEqualTo(ShopStatus.PENDING);
        assertThat(shop.name()).isEqualTo("Shop Áo Đẹp");
        assertThat(shop.slug()).isEqualTo("shop-ao-dep");
        assertThat(shop.description()).isNull();
        verify(users).promoteToSeller(7L);
    }

    @Test
    void registerAddsSuffixWhenSlugIsTaken() {
        when(shops.existsBySlugExcept("shop-ao-dep", -1)).thenReturn(true);
        when(shops.existsBySlugExcept("shop-ao-dep-2", -1)).thenReturn(true);

        assertThat(service.register(7L, new ShopInfoCommand("Shop Áo Đẹp", null, null)).slug())
                .isEqualTo("shop-ao-dep-3");
    }

    @Test
    void cannotRegisterTwice() {
        when(shops.findByOwnerId(7L)).thenReturn(Optional.of(Shop.register(7L, "A", "a", null, null)));

        assertThatThrownBy(() -> service.register(7L, new ShopInfoCommand("Shop B", null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SHOP_ALREADY_EXISTS);
        verify(users, never()).promoteToSeller(anyLong());
        verify(shops, never()).save(any());
    }

    @Test
    void updatingRejectedShopResubmitsItAndRegeneratesSlug() {
        Shop shop = Shop.register(7L, "Tên cũ", "ten-cu", null, null);
        ReflectionTestUtils.setField(shop, "id", 3L);
        shop.reject("Tên vi phạm");
        when(shops.findByOwnerId(7L)).thenReturn(Optional.of(shop));

        var updated = service.update(7L, new ShopInfoCommand("Tên Mới", "Mô tả", null));

        assertThat(updated.status()).isEqualTo(ShopStatus.PENDING);
        assertThat(updated.rejectReason()).isNull();
        assertThat(updated.slug()).isEqualTo("ten-moi");
        verify(shops).existsBySlugExcept(eq("ten-moi"), eq(3L));
    }

    @Test
    void activeShopKeepsSlugWhenRenamed() {
        Shop shop = Shop.register(7L, "Tên cũ", "ten-cu", null, null);
        shop.approve();
        when(shops.findByOwnerId(7L)).thenReturn(Optional.of(shop));

        var updated = service.update(7L, new ShopInfoCommand("Tên Mới", null, null));

        assertThat(updated.slug()).isEqualTo("ten-cu");
        assertThat(updated.name()).isEqualTo("Tên Mới");
        assertThat(updated.status()).isEqualTo(ShopStatus.ACTIVE);
    }

    @Test
    void approveAndRejectOnlyFromPending() {
        Shop shop = Shop.register(7L, "A", "a", null, null);
        shop.approve();

        assertThatThrownBy(() -> shop.reject("x"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOP_STATUS);
        assertThatThrownBy(shop::approve)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOP_STATUS);
    }
}
