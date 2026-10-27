package com.shopvi.shop.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import com.shopvi.shop.infra.ShopRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService implements ShopApi {

    private final ShopRepository shopRepository;

    @Override
    public Optional<ShopSummary> findActiveSummary(Long shopId) {
        return shopRepository.findByIdAndStatus(shopId, ShopStatus.ACTIVE).map(Shop::toSummary);
    }

    @Override
    public Map<Long, ShopSummary> findSummaries(Collection<Long> shopIds) {
        if (shopIds.isEmpty()) {
            return Map.of();
        }
        return shopRepository.findByIdIn(shopIds).stream()
                .collect(Collectors.toMap(Shop::getId, Shop::toSummary));
    }

    @Override
    public Set<Long> activeShopIds(Collection<Long> shopIds) {
        if (shopIds.isEmpty()) {
            return Set.of();
        }
        return shopRepository.findByIdIn(shopIds).stream()
                .filter(s -> s.getStatus() == ShopStatus.ACTIVE)
                .map(Shop::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<PickupAddress> findPickupAddress(Long shopId) {
        return shopRepository.findById(shopId).flatMap(Shop::pickupAddress);
    }

    @Override
    public Long requireShopIdOfOwner(Long ownerId) {
        return shopRepository.findByOwnerId(ownerId).map(Shop::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
    }

    @Override
    public Long requireActiveShopIdOfOwner(Long ownerId) {
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SHOP_NOT_ACTIVE);
        }
        return shop.getId();
    }
}
