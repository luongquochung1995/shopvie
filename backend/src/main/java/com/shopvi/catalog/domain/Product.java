package com.shopvi.catalog.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "shop_id", nullable = false, updatable = false)
    private Long shopId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    /** Thuộc tính tự do, vd. {"Thương hiệu":"Apple","Chất liệu":"Cotton"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> attributes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variant_options", nullable = false)
    private List<VariantOption> variantOptions;

    @Column(name = "min_price", nullable = false)
    private long minPrice;

    @Column(name = "max_price", nullable = false)
    private long maxPrice;

    @Column(name = "sold_count", nullable = false)
    private int soldCount;

    @Column(name = "rating_avg", nullable = false, precision = 2, scale = 1)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Lý do admin khóa (chỉ có khi BANNED). */
    @Column(name = "ban_reason")
    private String banReason;

    /**
     * Seller đăng sản phẩm mới. Slug tạm thời là duy nhất; service gán slug thật (kèm id) bằng {@link #assignSlug}
     * sau khi có id. Giá min/max được tính lại từ SKU bằng {@link #updatePriceRange}.
     */
    public static Product create(Long shopId, String temporarySlug, Details details, boolean visible) {
        Product product = new Product();
        product.shopId = shopId;
        product.slug = temporarySlug;
        product.ratingAvg = BigDecimal.ZERO;
        product.status = visible ? ProductStatus.ACTIVE : ProductStatus.HIDDEN;
        product.updateDetails(details);
        return product;
    }

    /** Slug giữ nguyên khi đổi tên để không làm hỏng link đã chia sẻ / link trong đơn hàng cũ. */
    public void assignSlug(String slug) {
        this.slug = slug;
    }

    public void updateDetails(Details d) {
        this.categoryId = d.categoryId();
        this.name = d.name();
        this.description = d.description();
        this.attributes = new LinkedHashMap<>(d.attributes());
        this.variantOptions = List.copyOf(d.variantOptions());
    }

    public void updatePriceRange(long minPrice, long maxPrice) {
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    /** Ẩn/hiện sản phẩm (ACTIVE ↔ HIDDEN). Sản phẩm bị admin khóa không tự mở lại được. */
    public void setVisible(boolean visible) {
        if (status == ProductStatus.BANNED) {
            throw new BusinessException(ErrorCode.PRODUCT_BANNED);
        }
        status = visible ? ProductStatus.ACTIVE : ProductStatus.HIDDEN;
    }

    /** Admin khóa sản phẩm vi phạm (hậu kiểm): ngừng hiển thị, seller không tự mở lại được. */
    public void ban(String reason) {
        if (status == ProductStatus.BANNED) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        status = ProductStatus.BANNED;
        banReason = reason;
    }

    /** Mở khóa: sản phẩm hiển thị lại (seller có thể ẩn nếu muốn). */
    public void unban() {
        if (status != ProductStatus.BANNED) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_STATUS);
        }
        status = ProductStatus.ACTIVE;
        banReason = null;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    /** Thông tin seller nhập (đã chuẩn hóa). */
    public record Details(Long categoryId, String name, String description, Map<String, Object> attributes,
                          List<VariantOption> variantOptions) {
    }
}
