package com.shopvi.cart.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cart_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private Long variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean selected;

    public static CartItem create(Long userId, Long variantId, int quantity) {
        CartItem item = new CartItem();
        item.userId = userId;
        item.variantId = variantId;
        item.quantity = quantity;
        item.selected = true;
        return item;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void select(boolean selected) {
        this.selected = selected;
    }
}
