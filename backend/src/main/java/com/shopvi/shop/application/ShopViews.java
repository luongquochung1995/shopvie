package com.shopvi.shop.application;

import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import com.shopvi.user.UserAccount;
import java.math.BigDecimal;
import java.time.Instant;

/** Dữ liệu vào/ra của Seller Center và trang duyệt shop. */
public final class ShopViews {

    private ShopViews() {
    }

    public record ShopInfoCommand(String name, String description, String logoUrl) {
    }

    /** Shop của seller đang đăng nhập (kèm trạng thái duyệt). */
    public record MyShop(Long id, String name, String slug, String logoUrl, String description, BigDecimal ratingAvg,
                         ShopStatus status, String rejectReason, PickupAddress pickupAddress, Instant createdAt,
                         Instant updatedAt) {
        public static MyShop of(Shop s) {
            return new MyShop(s.getId(), s.getName(), s.getSlug(), s.getLogoUrl(), s.getDescription(), s.getRatingAvg(),
                    s.getStatus(), s.getRejectReason(), s.pickupAddress().orElse(null), s.getCreatedAt(), s.getUpdatedAt());
        }
    }

    public record Owner(Long id, String email, String fullName) {
        static Owner of(UserAccount a) {
            return a == null ? null : new Owner(a.id(), a.email(), a.fullName());
        }
    }

    /** Dòng trong danh sách duyệt shop của admin. */
    public record AdminShop(Long id, String name, String slug, String logoUrl, String description, ShopStatus status,
                            String rejectReason, Owner owner, Instant createdAt, Instant updatedAt) {
        static AdminShop of(Shop s, UserAccount owner) {
            return new AdminShop(s.getId(), s.getName(), s.getSlug(), s.getLogoUrl(), s.getDescription(), s.getStatus(),
                    s.getRejectReason(), Owner.of(owner), s.getCreatedAt(), s.getUpdatedAt());
        }
    }
}
