package com.shopvi.promotion.infra;

import com.shopvi.promotion.domain.FlashSaleItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    @Query("SELECT i FROM FlashSaleItem i JOIN FETCH i.flashSale WHERE i.id = :id")
    Optional<FlashSaleItem> findWithSale(Long id);

    /** Cộng số đã bán; 0 nếu vượt quota (DB là chốt chặn cuối, Redis đã chặn trước). */
    @Modifying
    @Query(value = """
            UPDATE flash_sale_items SET sold = sold + :quantity, updated_at = now()
            WHERE id = :id AND sold + :quantity <= quota""", nativeQuery = true)
    int addSold(Long id, int quantity);
}
