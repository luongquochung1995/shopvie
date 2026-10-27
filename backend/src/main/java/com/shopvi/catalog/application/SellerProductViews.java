package com.shopvi.catalog.application;

import com.shopvi.catalog.application.ProductViews.CategoryRef;
import com.shopvi.catalog.application.ProductViews.VariantView;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.domain.VariantOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dữ liệu vào/ra của quản lý sản phẩm trong Seller Center. Tiền tệ: VND. */
public final class SellerProductViews {

    private SellerProductViews() {
    }

    /**
     * Tạo/sửa sản phẩm. {@code variants} phải có đúng một SKU cho mỗi tổ hợp giá trị của {@code variantOptions}
     * (không có nhóm phân loại → đúng 1 SKU với {@code optionValues} rỗng).
     *
     * @param visible chỉ dùng khi tạo mới: true = đăng bán ngay (ACTIVE), false = ẩn (HIDDEN)
     */
    public record ProductCommand(Long categoryId, String name, String description, Map<String, String> attributes,
                                 List<String> images, List<VariantOption> variantOptions,
                                 List<VariantCommand> variants, Boolean visible) {
    }

    /**
     * @param id  SKU đang có của sản phẩm (sửa) hoặc null (tạo mới); SKU cũ không có trong danh sách sẽ bị xóa
     * @param sku mã SKU; bỏ trống thì hệ thống tự sinh
     */
    public record VariantCommand(Long id, Map<String, String> optionValues, long price, int stock, String sku,
                                 String imageUrl) {
    }

    /** Dòng trong danh sách sản phẩm của seller. */
    public record SellerProductRow(Long id, String name, String slug, String thumbnailUrl, ProductStatus status,
                                   String banReason, long minPrice, long maxPrice, int totalStock, int variantCount, int soldCount,
                                   BigDecimal ratingAvg, Instant createdAt, Instant updatedAt) {
    }

    /** Đầy đủ dữ liệu để mở form sửa sản phẩm. */
    public record SellerProductDetail(Long id, String name, String slug, ProductStatus status, String banReason,
                                      Long categoryId,
                                      List<CategoryRef> categoryPath, String description,
                                      Map<String, Object> attributes, List<String> images,
                                      List<VariantOption> variantOptions, List<VariantView> variants,
                                      long minPrice, long maxPrice, int totalStock, int soldCount,
                                      BigDecimal ratingAvg, int ratingCount, Instant createdAt, Instant updatedAt) {
    }

    /** Bộ lọc danh sách: trạng thái và từ khóa trong tên (đều tùy chọn). */
    public record SellerProductFilter(ProductStatus status, String keyword) {
    }
}
