package com.shopvi.shop.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.application.ShopViews.AdminShop;
import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import com.shopvi.shop.infra.ShopRepository;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin duyệt / từ chối shop mới đăng ký. */
@Service
@RequiredArgsConstructor
@Transactional
public class ShopAdminService {

    private final ShopRepository shopRepository;
    private final UserApi userApi;

    @Transactional(readOnly = true)
    public Page<AdminShop> list(ShopStatus status, Pageable pageable) {
        Page<Shop> page = status == null ? shopRepository.findAll(pageable) : shopRepository.findByStatus(status, pageable);
        Map<Long, UserAccount> owners = userApi.findByIds(page.getContent().stream().map(Shop::getOwnerId).toList());
        List<AdminShop> items = page.getContent().stream()
                .map(s -> AdminShop.of(s, owners.get(s.getOwnerId())))
                .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    public AdminShop approve(Long shopId) {
        Shop shop = get(shopId);
        shop.approve();
        return toView(shop);
    }

    public AdminShop reject(Long shopId, String reason) {
        Shop shop = get(shopId);
        shop.reject(reason.trim());
        return toView(shop);
    }

    private Shop get(Long shopId) {
        return shopRepository.findById(shopId).orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    }

    private AdminShop toView(Shop shop) {
        shopRepository.flush();
        return AdminShop.of(shop, userApi.findById(shop.getOwnerId()).orElse(null));
    }
}
