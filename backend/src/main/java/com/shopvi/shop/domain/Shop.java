package com.shopvi.shop.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.ShopSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "shops")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shop extends BaseEntity {

    @Column(name = "owner_id", nullable = false, unique = true, updatable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "rating_avg", nullable = false, precision = 2, scale = 1)
    private BigDecimal ratingAvg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopStatus status;

    /** Lý do admin từ chối (chỉ có khi REJECTED). */
    @Column(name = "reject_reason")
    private String rejectReason;

    /** Lý do admin khóa shop (chỉ có khi BANNED). */
    @Column(name = "ban_reason")
    private String banReason;

    // Địa chỉ lấy hàng (nơi đơn vị vận chuyển đến lấy hàng)
    @Column(name = "pickup_name")
    private String pickupName;

    @Column(name = "pickup_phone")
    private String pickupPhone;

    @Column(name = "pickup_province_code")
    private String pickupProvinceCode;

    @Column(name = "pickup_ward_code")
    private String pickupWardCode;

    @Column(name = "pickup_detail")
    private String pickupDetail;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Đăng ký mở shop: chờ admin duyệt. */
    public static Shop register(Long ownerId, String name, String slug, String description, String logoUrl) {
        Shop shop = new Shop();
        shop.ownerId = ownerId;
        shop.ratingAvg = BigDecimal.ZERO;
        shop.status = ShopStatus.PENDING;
        shop.applyInfo(name, slug, description, logoUrl);
        return shop;
    }

    /**
     * Seller sửa thông tin shop. Shop bị từ chối được gửi duyệt lại (REJECTED → PENDING); shop bị khóa không sửa được.
     */
    public void updateInfo(String name, String slug, String description, String logoUrl) {
        if (status == ShopStatus.BANNED) {
            throw new BusinessException(ErrorCode.INVALID_SHOP_STATUS, "Banned shops cannot be edited");
        }
        applyInfo(name, slug, description, logoUrl);
        if (status == ShopStatus.REJECTED) {
            status = ShopStatus.PENDING;
            rejectReason = null;
        }
    }

    public void approve() {
        requireStatus(ShopStatus.PENDING);
        status = ShopStatus.ACTIVE;
        rejectReason = null;
    }

    public void reject(String reason) {
        requireStatus(ShopStatus.PENDING);
        status = ShopStatus.REJECTED;
        rejectReason = reason;
    }

    /** Khóa shop đang hoạt động: sản phẩm ngừng hiển thị, seller không xử lý được đơn mới. */
    public void ban(String reason) {
        requireStatus(ShopStatus.ACTIVE);
        status = ShopStatus.BANNED;
        banReason = reason;
    }

    public void unban() {
        requireStatus(ShopStatus.BANNED);
        status = ShopStatus.ACTIVE;
        banReason = null;
    }

    /** Shop chưa từng hiển thị công khai thì đổi slug theo tên mới được (không làm hỏng link đã chia sẻ). */
    public boolean isSlugChangeable() {
        return status == ShopStatus.PENDING || status == ShopStatus.REJECTED;
    }

    public void updatePickupAddress(PickupAddress a) {
        this.pickupName = a.name();
        this.pickupPhone = a.phone();
        this.pickupProvinceCode = a.provinceCode();
        this.pickupWardCode = a.wardCode();
        this.pickupDetail = a.detail();
    }

    public Optional<PickupAddress> pickupAddress() {
        return pickupProvinceCode == null ? Optional.empty()
                : Optional.of(new PickupAddress(pickupName, pickupPhone, pickupProvinceCode, pickupWardCode, pickupDetail));
    }

    public ShopSummary toSummary() {
        return new ShopSummary(getId(), name, slug, logoUrl, ratingAvg);
    }

    private void applyInfo(String name, String slug, String description, String logoUrl) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.logoUrl = logoUrl;
    }

    private void requireStatus(ShopStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_SHOP_STATUS,
                    "Shop must be %s but is %s".formatted(expected, status));
        }
    }
}
