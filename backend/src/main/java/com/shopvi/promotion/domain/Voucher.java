package com.shopvi.promotion.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Voucher sàn ({@code shopId = null}) hoặc voucher của một shop. */
@Getter
@Entity
@Table(name = "vouchers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Voucher extends BaseEntity {

    public enum Type { FIXED, PERCENT, FREESHIP }

    public enum Status { ACTIVE, DISABLED }

    @Column(name = "shop_id", updatable = false)
    private Long shopId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Column(nullable = false, updatable = false)
    private long value;

    @Column(name = "min_order_value", nullable = false, updatable = false)
    private long minOrderValue;

    @Column(name = "max_discount", updatable = false)
    private Long maxDiscount;

    @Column(name = "usage_limit", nullable = false, updatable = false)
    private int usageLimit;

    /** Chỉ đổi bằng câu UPDATE nguyên tử ở repository. */
    @Column(name = "used_count", nullable = false, insertable = false, updatable = false)
    private int usedCount;

    @Column(name = "per_user_limit", nullable = false, updatable = false)
    private int perUserLimit;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public static Voucher create(Long shopId, String code, String name, Type type, long value, long minOrderValue,
                                 Long maxDiscount, int usageLimit, int perUserLimit, Instant startAt, Instant endAt) {
        Voucher v = new Voucher();
        v.shopId = shopId;
        v.code = code;
        v.name = name;
        v.type = type;
        v.value = value;
        v.minOrderValue = minOrderValue;
        v.maxDiscount = type == Type.PERCENT ? maxDiscount : null;
        v.usageLimit = usageLimit;
        v.perUserLimit = perUserLimit;
        v.startAt = startAt;
        v.endAt = endAt;
        v.status = Status.ACTIVE;
        return v;
    }

    public boolean isPlatform() {
        return shopId == null;
    }

    public boolean isRunning(Instant now) {
        return status == Status.ACTIVE && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    public void disable() {
        status = Status.DISABLED;
    }

    /**
     * Mức giảm cho một khoản tiền (tiền hàng, hoặc phí ship với FREESHIP); không vượt quá khoản đó.
     *
     * @param merchandise tiền hàng áp voucher (để tính FIXED/PERCENT)
     * @param shipping    phí ship áp voucher (để tính FREESHIP)
     */
    public long discountFor(long merchandise, long shipping) {
        long discount = switch (type) {
            case FIXED -> value;
            case PERCENT -> {
                long percent = merchandise * value / 100;
                yield maxDiscount == null ? percent : Math.min(percent, maxDiscount);
            }
            case FREESHIP -> Math.min(value, shipping);
        };
        return Math.min(discount, type == Type.FREESHIP ? shipping : merchandise);
    }
}
