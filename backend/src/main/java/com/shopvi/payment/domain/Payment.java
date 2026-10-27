package com.shopvi.payment.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.PaymentState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một lần thanh toán online cho một checkout. {@code idempotencyKey} là mã giao dịch gửi sang cổng
 * (vnp_TxnRef / MoMo orderId) nên callback gọi lặp luôn trỏ về đúng bản ghi này.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "checkout_id", nullable = false, updatable = false)
    private UUID checkoutId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentChannel provider;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_txn_id")
    private String providerTxnId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentState status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** Dữ liệu callback gần nhất của cổng (để đối soát). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private Map<String, String> rawPayload;

    public static Payment start(UUID checkoutId, Long userId, PaymentChannel provider, String idempotencyKey,
                                long amount, Instant expiresAt) {
        Payment p = new Payment();
        p.checkoutId = checkoutId;
        p.userId = userId;
        p.provider = provider;
        p.idempotencyKey = idempotencyKey;
        p.amount = amount;
        p.expiresAt = expiresAt;
        p.status = PaymentState.PENDING;
        return p;
    }

    /**
     * Cổng báo thanh toán thành công. Ghi nhận cả khi giao dịch đã FAILED/EXPIRED ở phía ShopVi vì tiền đã bị trừ
     * (nơi xử lý sự kiện quyết định hoàn tiền nếu đơn đã bị hủy).
     */
    public void succeed(String providerTxnId, Map<String, String> raw, Instant now) {
        this.status = PaymentState.SUCCESS;
        this.providerTxnId = providerTxnId;
        this.rawPayload = raw;
        this.paidAt = now;
    }

    /** Cổng báo thất bại / người mua hủy: chỉ áp dụng khi còn PENDING. */
    public void fail(String providerTxnId, Map<String, String> raw) {
        if (status == PaymentState.PENDING) {
            this.status = PaymentState.FAILED;
            this.providerTxnId = providerTxnId;
            this.rawPayload = raw;
        }
    }

    public void expire() {
        if (status == PaymentState.PENDING) {
            status = PaymentState.EXPIRED;
        }
    }

    public boolean isSucceeded() {
        return status == PaymentState.SUCCESS;
    }
}
