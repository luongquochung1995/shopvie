package com.shopvi.catalog.infra;

import com.shopvi.catalog.domain.Product;
import com.shopvi.catalog.domain.ProductStatus;
import java.util.Collection;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Điều kiện lọc sản phẩm cho {@code GET /products} và danh sách sản phẩm của seller. */
public final class ProductSpecs {

    private ProductSpecs() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return (root, query, cb) -> root.get("categoryId").in(categoryIds);
    }

    /** Lọc theo giá thấp nhất của sản phẩm (giá "từ ..." hiển thị trên thẻ sản phẩm). */
    public static Specification<Product> minPriceAtLeast(long minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("minPrice"), minPrice);
    }

    public static Specification<Product> minPriceAtMost(long maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("minPrice"), maxPrice);
    }

    public static Specification<Product> ofShop(Long shopId) {
        return (root, query, cb) -> cb.equal(root.get("shopId"), shopId);
    }

    /** Tên chứa chuỗi tìm kiếm (không phân biệt hoa thường; ký tự % và _ được hiểu theo nghĩa đen). */
    public static Specification<Product> nameContains(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\');
    }
}
