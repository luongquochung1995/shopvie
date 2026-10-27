package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.FlashSale;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    /** Phiên đang chạy hoặc sắp diễn ra (kết thúc sau {@code now}), sớm nhất trước. */
    @Query("""
            SELECT s FROM FlashSale s
            WHERE s.status = com.shopvi.promotion.domain.FlashSale.Status.ACTIVE AND s.endAt > :now
            ORDER BY s.startAt, s.id""")
    List<FlashSale> findCurrentAndUpcoming(Instant now);

    Page<FlashSale> findAllByOrderByStartAtDesc(Pageable pageable);
}
