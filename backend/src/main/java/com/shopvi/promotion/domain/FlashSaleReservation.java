package com.shopvi.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * Kết quả cuối của một lượt giữ hàng flash sale (trạng thái PENDING chỉ nằm trên Redis). Luôn INSERT (không merge):
 * khóa chính là id lượt giữ nên tạo đơn và hủy lượt giữ chạy đua nhau thì chỉ một bên ghi được.
 */
@Getter
@Entity
@Table(name = "flash_sale_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlashSaleReservation implements Persistable<UUID> {

    public enum Status { CONFIRMED, FAILED }

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Status status;

    @Column(name = "order_code", updatable = false)
    private String orderCode;

    @Column(name = "failure_reason", updatable = false)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }

    public static FlashSaleReservation confirmed(UUID id, Long itemId, Long userId, int quantity, String orderCode) {
        return of(id, itemId, userId, quantity, Status.CONFIRMED, orderCode, null);
    }

    public static FlashSaleReservation failed(UUID id, Long itemId, Long userId, int quantity, String reason) {
        return of(id, itemId, userId, quantity, Status.FAILED, null, reason);
    }

    private static FlashSaleReservation of(UUID id, Long itemId, Long userId, int quantity, Status status,
                                           String orderCode, String reason) {
        FlashSaleReservation r = new FlashSaleReservation();
        r.id = id;
        r.itemId = itemId;
        r.userId = userId;
        r.quantity = quantity;
        r.status = status;
        r.orderCode = orderCode;
        r.failureReason = reason;
        return r;
    }
}
