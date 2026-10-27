package com.shopvi.catalog.infra;

import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    /** [slug, updatedAt, shopId] của sản phẩm đang bán, mới cập nhật trước (sitemap). */
    @Query("SELECT p.slug, p.updatedAt, p.shopId FROM Product p WHERE p.status = com.shopvi.catalog.domain.ProductStatus.ACTIVE ORDER BY p.updatedAt DESC")
    List<Object[]> findSitemapEntries(Pageable pageable);

    /** Slug kể cả sản phẩm đã xóa mềm (để xóa cache chi tiết). */
    @Query(value = "SELECT slug FROM products WHERE id = :id", nativeQuery = true)
    Optional<String> findSlugIncludingDeleted(Long id);

    @Query("SELECT p.id FROM Product p WHERE p.shopId = :shopId")
    List<Long> findIdsByShopId(Long shopId);

    @Query("SELECT p.status, COUNT(p) FROM Product p GROUP BY p.status")
    List<Object[]> countGroupByStatus();

    /** Sản phẩm thuộc shop (kiểm tra quyền sở hữu: của shop khác coi như không tồn tại). */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.status = com.shopvi.catalog.domain.ProductStatus.ACTIVE AND p.id > :afterId ORDER BY p.id""")
    List<Long> findActiveIdsAfter(long afterId, Pageable pageable);

    /** Cộng/trừ số đã bán (không xuống dưới 0). */
    @Modifying
    @Query(value = """
            UPDATE products SET sold_count = GREATEST(sold_count + :delta, 0), updated_at = now()
            WHERE id = :id""", nativeQuery = true)
    int addSoldCount(Long id, int delta);

    @Modifying
    @Query(value = """
            UPDATE products SET rating_avg = :avg, rating_count = :count, updated_at = now()
            WHERE id = :id""", nativeQuery = true)
    int updateRating(Long id, BigDecimal avg, int count);
}
