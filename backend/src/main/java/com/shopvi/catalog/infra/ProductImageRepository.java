package com.shopvi.catalog.infra;

import com.shopvi.catalog.domain.ProductImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAscIdAsc(Long productId);

    List<ProductImage> findByProductIdInOrderByProductIdAscPositionAscIdAsc(Collection<Long> productIds);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("DELETE FROM ProductImage i WHERE i.productId = :productId")
    int deleteByProductId(Long productId);
}
