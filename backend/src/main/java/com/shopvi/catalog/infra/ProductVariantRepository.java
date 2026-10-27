package com.shopvi.catalog.infra;

import com.shopvi.catalog.domain.ProductVariant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderByIdAsc(Long productId);

    List<ProductVariant> findByIdIn(Collection<Long> ids);

    List<ProductVariant> findBySkuIn(Collection<String> skus);

    boolean existsBySku(String sku);

    /** Tổng tồn kho và số SKU của từng sản phẩm (danh sách sản phẩm của seller). */
    @Query("""
            SELECT v.productId AS productId, SUM(v.stock) AS totalStock, COUNT(v) AS variantCount
            FROM ProductVariant v WHERE v.productId IN :productIds GROUP BY v.productId""")
    List<StockSummary> summarizeStock(Collection<Long> productIds);

    /** Trừ kho nguyên tử: trả 0 nếu không đủ hàng (không bao giờ âm kho). */
    @Modifying
    @Query(value = """
            UPDATE product_variants SET stock = stock - :quantity, version = version + 1, updated_at = now()
            WHERE id = :id AND stock >= :quantity""", nativeQuery = true)
    int decreaseStock(Long id, int quantity);

    @Modifying
    @Query(value = """
            UPDATE product_variants SET stock = stock + :quantity, version = version + 1, updated_at = now()
            WHERE id = :id""", nativeQuery = true)
    int increaseStock(Long id, int quantity);

    interface StockSummary {
        Long getProductId();

        Long getTotalStock();

        Long getVariantCount();
    }
}
