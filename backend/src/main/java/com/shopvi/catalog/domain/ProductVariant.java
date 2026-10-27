package com.shopvi.catalog.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * SKU của sản phẩm. Tồn kho được trừ bằng câu UPDATE nguyên tử (Phase 2), {@code version} tăng theo.
 */
@Getter
@Entity
@Table(name = "product_variants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, unique = true)
    private String sku;

    /** Giá trị phân loại của SKU, vd. {"Màu sắc":"Đỏ","Kích cỡ":"M"}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_values", nullable = false)
    private Map<String, String> optionValues;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "image_url")
    private String imageUrl;

    @Version
    @Column(nullable = false)
    private int version;

    public static ProductVariant create(Long productId, String sku, Map<String, String> optionValues, long price,
                                        int stock, String imageUrl) {
        ProductVariant variant = new ProductVariant();
        variant.productId = productId;
        variant.update(sku, optionValues, price, stock, imageUrl);
        return variant;
    }

    /**
     * Seller sửa SKU (đặt lại tồn kho tuyệt đối). Nếu checkout vừa trừ kho trong lúc này thì {@code version} lệch
     * và Hibernate ném lỗi optimistic lock → 409, seller tải lại số liệu mới.
     */
    public void update(String sku, Map<String, String> optionValues, long price, int stock, String imageUrl) {
        this.sku = sku;
        this.optionValues = new LinkedHashMap<>(optionValues);
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
    }
}
