package com.shopvi.shop.infra;

import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByIdAndStatus(Long id, ShopStatus status);

    List<Shop> findByIdIn(Collection<Long> ids);

    Optional<Shop> findByOwnerId(Long ownerId);

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    @Query("SELECT s.id FROM Shop s WHERE s.status = :status")
    Set<Long> findIdsByStatus(ShopStatus status);

    @Query("SELECT s.status, COUNT(s) FROM Shop s GROUP BY s.status")
    List<Object[]> countGroupByStatus();

    /** Tính cả shop đã xóa mềm vì ràng buộc UNIQUE của cột slug áp dụng cho mọi dòng. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM shops WHERE slug = :slug AND id <> :excludeId)", nativeQuery = true)
    boolean existsBySlugExcept(String slug, long excludeId);

    @Modifying
    @Query(value = "UPDATE shops SET rating_avg = :avg, updated_at = now() WHERE id = :id", nativeQuery = true)
    int updateRating(Long id, BigDecimal avg);
}
