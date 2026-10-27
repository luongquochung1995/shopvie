package com.shopvi.shop.infra;

import com.shopvi.shop.domain.Shop;
import com.shopvi.shop.domain.ShopStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByIdAndStatus(Long id, ShopStatus status);

    List<Shop> findByIdIn(Collection<Long> ids);

    Optional<Shop> findByOwnerId(Long ownerId);

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    /** Tính cả shop đã xóa mềm vì ràng buộc UNIQUE của cột slug áp dụng cho mọi dòng. */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM shops WHERE slug = :slug AND id <> :excludeId)", nativeQuery = true)
    boolean existsBySlugExcept(String slug, long excludeId);
}
