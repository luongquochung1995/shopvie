package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.VoucherUsage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    /** Số lần (checkout) người dùng đã dùng voucher. */
    @Query("SELECT COUNT(DISTINCT u.checkoutId) FROM VoucherUsage u WHERE u.voucherId = :voucherId AND u.userId = :userId")
    long countCheckouts(Long voucherId, Long userId);

    @Query("""
            SELECT u.voucherId AS voucherId, COUNT(DISTINCT u.checkoutId) AS count FROM VoucherUsage u
            WHERE u.userId = :userId AND u.voucherId IN :voucherIds GROUP BY u.voucherId""")
    List<UsageCount> countCheckouts(Long userId, Collection<Long> voucherIds);

    List<VoucherUsage> findByOrderId(Long orderId);

    boolean existsByVoucherIdAndCheckoutId(Long voucherId, UUID checkoutId);

    interface UsageCount {
        Long getVoucherId();

        long getCount();
    }
}
