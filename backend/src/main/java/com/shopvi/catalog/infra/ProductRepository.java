package com.shopvi.catalog.infra;

import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    /** Sản phẩm thuộc shop (kiểm tra quyền sở hữu: của shop khác coi như không tồn tại). */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    boolean existsByCategoryId(Long categoryId);

    /** Cộng/trừ số đã bán (không xuống dưới 0). */
    @Modifying
    @Query(value = """
            UPDATE products SET sold_count = GREATEST(sold_count + :delta, 0), updated_at = now()
            WHERE id = :id""", nativeQuery = true)
    int addSoldCount(Long id, int delta);
}
