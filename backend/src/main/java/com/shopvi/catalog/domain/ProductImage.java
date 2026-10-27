package com.shopvi.catalog.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private int position;

    public static ProductImage of(Long productId, String url, int position) {
        ProductImage image = new ProductImage();
        image.productId = productId;
        image.url = url;
        image.position = position;
        return image;
    }
}
