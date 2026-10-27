package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.Voucher;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    @Query("SELECT v FROM Voucher v WHERE UPPER(v.code) IN :codes")
    List<Voucher> findByCodes(Collection<String> codes);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM vouchers WHERE upper(code) = upper(:code))", nativeQuery = true)
    boolean existsByCode(String code);

    Page<Voucher> findByShopId(Long shopId, Pageable pageable);

    Page<Voucher> findByShopIdIsNull(Pageable pageable);

    Optional<Voucher> findByIdAndShopId(Long id, Long shopId);

    /** Voucher đang chạy, còn lượt, của sàn hoặc của các shop cho trước. */
    @Query("""
            SELECT v FROM Voucher v
            WHERE v.status = com.shopvi.promotion.domain.Voucher.Status.ACTIVE
              AND v.startAt <= :now AND v.endAt > :now AND v.usedCount < v.usageLimit
              AND (v.shopId IS NULL OR v.shopId IN :shopIds)
            ORDER BY v.endAt""")
    List<Voucher> findRunning(Collection<Long> shopIds, Instant now);

    /** Tăng lượt dùng nguyên tử: 0 dòng = hết lượt / hết hạn / bị tắt (khóa dòng tới hết transaction). */
    @Modifying
    @Query(value = """
            UPDATE vouchers SET used_count = used_count + 1, updated_at = now()
            WHERE id = :id AND used_count < usage_limit AND status = 'ACTIVE' AND start_at <= :now AND end_at > :now""",
            nativeQuery = true)
    int incrementUsage(Long id, Instant now);

    @Modifying
    @Query(value = "UPDATE vouchers SET used_count = GREATEST(used_count - 1, 0), updated_at = now() WHERE id = :id",
            nativeQuery = true)
    int decrementUsage(Long id);
}
