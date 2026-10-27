package com.shopvi.catalog.web;

import static com.shopvi.common.util.ValidationPatterns.HTTP_URL;
import static com.shopvi.common.util.ValidationPatterns.HTTP_URL_MESSAGE;

import com.shopvi.catalog.application.SellerProductService;
import com.shopvi.catalog.application.SellerProductViews.ProductCommand;
import com.shopvi.catalog.application.SellerProductViews.SellerProductDetail;
import com.shopvi.catalog.application.SellerProductViews.SellerProductFilter;
import com.shopvi.catalog.application.SellerProductViews.SellerProductRow;
import com.shopvi.catalog.application.SellerProductViews.VariantCommand;
import com.shopvi.catalog.application.VariantMatrix;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Products", description = "Sản phẩm, phân loại và SKU của shop (role SELLER)")
@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
class SellerProductController {

    private static final Set<String> SORTABLE = Set.of("updatedAt", "createdAt", "soldCount", "minPrice");

    private final SellerProductService sellerProductService;

    @Operation(summary = "Danh sách sản phẩm của shop, lọc theo trạng thái và tên",
            description = "sort: updatedAt,desc (mặc định) | createdAt | soldCount | minPrice")
    @GetMapping
    ApiResponse<PageResponse<SellerProductRow>> list(
            @CurrentUserId Long sellerId,
            @Parameter(description = "ACTIVE | HIDDEN | BANNED ...; bỏ trống = tất cả")
            @RequestParam(required = false) ProductStatus status,
            @Parameter(description = "Từ khóa trong tên sản phẩm") @RequestParam(required = false) @Size(max = 100) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported sort property: " + order.getProperty());
            }
        }
        Pageable stable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.from(
                sellerProductService.list(sellerId, new SellerProductFilter(status, q), stable)));
    }

    @Operation(summary = "Chi tiết sản phẩm để sửa: phân loại, toàn bộ SKU, ảnh, thuộc tính")
    @GetMapping("/{id}")
    ApiResponse<SellerProductDetail> get(@CurrentUserId Long sellerId, @PathVariable Long id) {
        return ApiResponse.ok(sellerProductService.get(sellerId, id));
    }

    @Operation(summary = "Đăng sản phẩm mới (shop phải đang hoạt động)",
            description = "variants: đúng 1 SKU cho mỗi tổ hợp giá trị của variantOptions (tối đa 2 nhóm, "
                    + VariantMatrix.MAX_VARIANTS + " tổ hợp); không có phân loại → 1 SKU với optionValues rỗng. "
                    + "Danh mục phải là danh mục cấp thấp nhất. visible=false để lưu ở trạng thái ẩn.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SellerProductDetail> create(@CurrentUserId Long sellerId, @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(sellerProductService.create(sellerId, req.toCommand()));
    }

    @Operation(summary = "Sửa sản phẩm",
            description = "SKU có id = sửa; không có id = thêm mới; SKU cũ không gửi lên sẽ bị xóa (khỏi giỏ hàng). "
                    + "Tồn kho là số tuyệt đối; nếu có đơn vừa trừ kho cùng lúc → 409 CONFLICT, tải lại rồi lưu lại.")
    @PutMapping("/{id}")
    ApiResponse<SellerProductDetail> update(@CurrentUserId Long sellerId, @PathVariable Long id,
                                            @Valid @RequestBody ProductRequest req) {
        return ApiResponse.ok(sellerProductService.update(sellerId, id, req.toCommand()));
    }

    @Operation(summary = "Ẩn / hiện sản phẩm (HIDDEN ↔ ACTIVE)")
    @PatchMapping("/{id}/visibility")
    ApiResponse<SellerProductDetail> setVisibility(@CurrentUserId Long sellerId, @PathVariable Long id,
                                                   @Valid @RequestBody VisibilityRequest req) {
        return ApiResponse.ok(sellerProductService.setVisible(sellerId, id, req.visible()));
    }

    @Operation(summary = "Xóa sản phẩm (xóa mềm; đơn hàng cũ vẫn giữ thông tin đã chụp lại)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUserId Long sellerId, @PathVariable Long id) {
        sellerProductService.delete(sellerId, id);
    }

    record ProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(min = 3, max = 255) String name,
            @Size(max = 5000) String description,
            @Size(max = 20) Map<@NotBlank @Size(max = 50) String, @NotNull @Size(max = 200) String> attributes,
            @NotEmpty @Size(max = 9)
            List<@NotBlank @Size(max = 500) @Pattern(regexp = HTTP_URL, message = HTTP_URL_MESSAGE) String> images,
            @Size(max = VariantMatrix.MAX_OPTION_GROUPS) List<@Valid @NotNull OptionRequest> variantOptions,
            @NotEmpty @Size(max = VariantMatrix.MAX_VARIANTS) List<@Valid @NotNull VariantRequest> variants,
            Boolean visible) {

        ProductCommand toCommand() {
            return new ProductCommand(categoryId, name, description, attributes, images,
                    variantOptions == null ? List.of()
                            : variantOptions.stream().map(o -> new VariantOption(o.name(), o.values())).toList(),
                    variants.stream().map(VariantRequest::toCommand).toList(), visible);
        }
    }

    record OptionRequest(@NotBlank @Size(max = 30) String name,
                         @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 30) String> values) {
    }

    record VariantRequest(
            Long id,
            Map<@NotBlank @Size(max = 30) String, @NotBlank @Size(max = 30) String> optionValues,
            @Min(1_000) @Max(999_999_999) long price,
            @Min(0) @Max(999_999) int stock,
            @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "may contain letters, digits, . _ -")
            String sku,
            @Size(max = 500) @Pattern(regexp = HTTP_URL, message = HTTP_URL_MESSAGE) String imageUrl) {

        VariantCommand toCommand() {
            return new VariantCommand(id, optionValues, price, stock, sku, imageUrl);
        }
    }

    record VisibilityRequest(@NotNull Boolean visible) {
    }
}
