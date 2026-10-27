package com.shopvi.promotion.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một SKU trong phiên flash sale: giá sale, quota bán, giới hạn mỗi người. */
@Getter
@Entity
@Table(name = "flash_sale_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flash_sale_id", nullable = false, updatable = false)
    private FlashSale flashSale;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "sale_price", nullable = false, updatable = false)
    private long salePrice;

    @Column(nullable = false, updatable = false)
    private int quota;

    /** Cộng bằng câu UPDATE nguyên tử khi đơn flash sale được tạo. */
    @Column(nullable = false, insertable = false, updatable = false)
    private int sold;

    @Column(name = "per_user_limit", nullable = false, updatable = false)
    private int perUserLimit;

    static FlashSaleItem of(FlashSale sale, Long variantId, Long productId, long salePrice, int quota, int perUserLimit) {
        FlashSaleItem i = new FlashSaleItem();
        i.flashSale = sale;
        i.variantId = variantId;
        i.productId = productId;
        i.salePrice = salePrice;
        i.quota = quota;
        i.perUserLimit = perUserLimit;
        return i;
    }
}
