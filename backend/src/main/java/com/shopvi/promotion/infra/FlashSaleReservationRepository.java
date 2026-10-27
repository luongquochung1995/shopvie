package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.FlashSaleReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FlashSaleReservationRepository extends JpaRepository<FlashSaleReservation, UUID> {

    /** Số đã mua (đã có đơn) theo người của một item: {@code [userId, quantity]}. */
    @Query("""
            SELECT r.userId, SUM(r.quantity) FROM FlashSaleReservation r
            WHERE r.itemId = :itemId AND r.status = com.shopvi.promotion.domain.FlashSaleReservation.Status.CONFIRMED
            GROUP BY r.userId""")
    List<Object[]> confirmedQuantityByUser(Long itemId);
}
