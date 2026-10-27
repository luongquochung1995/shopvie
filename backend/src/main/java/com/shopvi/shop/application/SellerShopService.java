package com.shopvi.shop.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.util.SlugUtils;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.application.ShopViews.MyShop;
import com.shopvi.shop.application.ShopViews.ShopInfoCommand;
import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.infra.ShopRepository;
import com.shopvi.user.UserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Đăng ký mở shop và quản lý thông tin shop của seller. */
@Service
@RequiredArgsConstructor
@Transactional
public class SellerShopService {

    private final ShopRepository shopRepository;
    private final UserApi userApi;

    /**
     * Mở shop: tạo shop PENDING (chờ admin duyệt) và nâng tài khoản lên SELLER trong cùng transaction.
     * Client cần làm mới access token để nhận role mới.
     */
    public MyShop register(Long userId, ShopInfoCommand cmd) {
        if (shopRepository.findByOwnerId(userId).isPresent()) {
            throw new BusinessException(ErrorCode.SHOP_ALREADY_EXISTS);
        }
        userApi.promoteToSeller(userId);
        String name = cmd.name().trim();
        Shop shop = shopRepository.save(Shop.register(userId, name, uniqueSlug(name, -1),
                trimToNull(cmd.description()), trimToNull(cmd.logoUrl())));
        return MyShop.of(shop);
    }

    @Transactional(readOnly = true)
    public MyShop myShop(Long userId) {
        return MyShop.of(get(userId));
    }

    /** Sửa thông tin; shop bị từ chối sẽ được gửi duyệt lại. */
    public MyShop update(Long userId, ShopInfoCommand cmd) {
        Shop shop = get(userId);
        String name = cmd.name().trim();
        String slug = shop.isSlugChangeable() ? uniqueSlug(name, shop.getId()) : shop.getSlug();
        shop.updateInfo(name, slug, trimToNull(cmd.description()), trimToNull(cmd.logoUrl()));
        shopRepository.flush();
        return MyShop.of(shop);
    }

    /** Địa chỉ lấy hàng: nơi đơn vị vận chuyển đến lấy hàng, dùng để tính phí ship và tạo vận đơn. */
    public MyShop updatePickupAddress(Long userId, PickupAddress address) {
        Shop shop = get(userId);
        shop.updatePickupAddress(new PickupAddress(address.name().trim(), address.phone().trim(), address.provinceCode(),
                address.wardCode(), address.detail().trim()));
        shopRepository.flush();
        return MyShop.of(shop);
    }

    private Shop get(Long userId) {
        return shopRepository.findByOwnerId(userId).orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    }

    /** "ShopVi Fashion" → "shopvi-fashion", trùng thì thêm hậu tố "-2", "-3"... */
    private String uniqueSlug(String name, long excludeId) {
        String base = SlugUtils.slugify(name);
        if (base.isEmpty()) {
            base = "shop";
        }
        String slug = base;
        for (int i = 2; shopRepository.existsBySlugExcept(slug, excludeId); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
