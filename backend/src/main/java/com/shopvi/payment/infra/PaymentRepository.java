package com.shopvi.payment.infra;

import com.shopvi.payment.PaymentState;
import com.shopvi.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Khóa dòng khi xử lý callback: IPN và return URL đến cùng lúc chỉ một bên được xử lý. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.idempotencyKey = :key")
    Optional<Payment> findByIdempotencyKeyForUpdate(String key);

    Optional<Payment> findByIdempotencyKey(String key);

    Optional<Payment> findFirstByCheckoutIdOrderByIdDesc(UUID checkoutId);

    List<Payment> findByCheckoutIdAndStatus(UUID checkoutId, PaymentState status);
}
