package com.shopvi.promotion.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lượt áp voucher vào một đơn (voucher sàn: mỗi đơn của checkout một dòng, cùng checkoutId). */
@Getter
@Entity
@Table(name = "voucher_usages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoucherUsage extends BaseEntity {

    @Column(name = "voucher_id", nullable = false, updatable = false)
    private Long voucherId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "checkout_id", nullable = false, updatable = false)
    private UUID checkoutId;

    @Column(nullable = false, updatable = false)
    private long amount;

    public static VoucherUsage of(Long voucherId, Long userId, Long orderId, UUID checkoutId, long amount) {
        VoucherUsage u = new VoucherUsage();
        u.voucherId = voucherId;
        u.userId = userId;
        u.orderId = orderId;
        u.checkoutId = checkoutId;
        u.amount = amount;
        return u;
    }
}
