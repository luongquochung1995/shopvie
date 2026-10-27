package com.shopvi.catalog.web;

import com.shopvi.catalog.application.ProductAdminService;
import com.shopvi.catalog.application.ProductAdminService.AdminProduct;
import com.shopvi.catalog.domain.ProductStatus;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Products", description = "Hậu kiểm sản phẩm: rà soát, khóa / mở khóa (role ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
class ProductAdminController {

    private final ProductAdminService productAdminService;

    @Operation(summary = "Danh sách sản phẩm (mới nhất trước), lọc theo trạng thái / shop / tên")
    @GetMapping
    ApiResponse<PageResponse<AdminProduct>> list(
            @Parameter(description = "ACTIVE | HIDDEN | BANNED; bỏ trống = tất cả") @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable newest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return ApiResponse.ok(PageResponse.from(productAdminService.list(status, shopId, q, newest)));
    }

    @Operation(summary = "Khóa sản phẩm vi phạm kèm lý do (ngừng hiển thị, seller được thông báo)")
    @PostMapping("/{id}/ban")
    ApiResponse<AdminProduct> ban(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return ApiResponse.ok(productAdminService.ban(id, req.reason()));
    }

    @Operation(summary = "Mở khóa sản phẩm (BANNED → ACTIVE)")
    @PostMapping("/{id}/unban")
    ApiResponse<AdminProduct> unban(@PathVariable Long id) {
        return ApiResponse.ok(productAdminService.unban(id));
    }

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
