package com.shopvi.catalog.web;

import com.shopvi.catalog.application.ProductQueryService;
import com.shopvi.catalog.application.ProductViews.ProductCard;
import com.shopvi.catalog.application.ProductViews.ProductDetail;
import com.shopvi.catalog.application.ProductViews.ProductFilter;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Danh sách và chi tiết sản phẩm (public)")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
class ProductController {

    /** createdAt = mới nhất, soldCount = bán chạy, minPrice = giá. */
    private static final Set<String> SORTABLE = Set.of("createdAt", "soldCount", "minPrice");

    private final ProductQueryService productQueryService;

    @Operation(summary = "Danh sách sản phẩm đang bán, lọc theo danh mục (gồm danh mục con) và khoảng giá",
            description = "sort: createdAt,desc (mới nhất) | soldCount,desc (bán chạy) | minPrice,asc|desc (giá)")
    @GetMapping
    ApiResponse<PageResponse<ProductCard>> list(
            @Parameter(description = "Id danh mục") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Giá từ (VND)") @RequestParam(required = false) @PositiveOrZero Long minPrice,
            @Parameter(description = "Giá đến (VND)") @RequestParam(required = false) @PositiveOrZero Long maxPrice,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                    List.of(new ErrorDetail("minPrice", "must be less than or equal to maxPrice")));
        }
        return ApiResponse.ok(productQueryService.listPage(new ProductFilter(categoryId, minPrice, maxPrice), stable(pageable)));
    }

    @Operation(summary = "Chi tiết sản phẩm theo slug: ảnh, phân loại, SKU, thông tin shop")
    @GetMapping("/{slug}")
    ApiResponse<ProductDetail> detail(@PathVariable String slug) {
        return ApiResponse.ok(productQueryService.getDetail(slug));
    }

    /** Chỉ cho phép sort theo whitelist và thêm id làm tiêu chí phụ để phân trang ổn định. */
    private static Pageable stable(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported sort property: " + order.getProperty());
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
