package com.shopvi.order.domain;

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

/**
 * Dòng đơn hàng — snapshot tên/ảnh/giá tại thời điểm đặt.
 */
@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "variant_id", updatable = false)
    private Long variantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "product_slug", nullable = false, updatable = false)
    private String productSlug;

    @Column(name = "variant_name", updatable = false)
    private String variantName;

    @Column(name = "image_url", updatable = false)
    private String imageUrl;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    static OrderItem of(Order order, Snapshot s) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.variantId = s.variantId();
        item.productId = s.productId();
        item.productName = s.productName();
        item.productSlug = s.productSlug();
        item.variantName = s.variantName();
        item.imageUrl = s.imageUrl();
        item.unitPrice = s.unitPrice();
        item.quantity = s.quantity();
        return item;
    }

    public long lineTotal() {
        return unitPrice * quantity;
    }

    /** Dữ liệu chụp lại từ SKU lúc đặt hàng. */
    public record Snapshot(Long variantId, Long productId, String productName, String productSlug, String variantName,
                           String imageUrl, long unitPrice, int quantity) {
    }
}
