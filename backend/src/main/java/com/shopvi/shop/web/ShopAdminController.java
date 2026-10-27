package com.shopvi.shop.web;

import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.shop.application.ShopAdminService;
import com.shopvi.shop.application.ShopViews.AdminShop;
import com.shopvi.shop.domain.ShopStatus;
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

@Tag(name = "Admin - Shops", description = "Duyệt shop mới đăng ký (role ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/shops")
@RequiredArgsConstructor
class ShopAdminController {

    private final ShopAdminService shopAdminService;

    @Operation(summary = "Danh sách shop theo trạng thái (cũ nhất trước — hàng đợi duyệt)")
    @GetMapping
    ApiResponse<PageResponse<AdminShop>> list(
            @Parameter(description = "PENDING | ACTIVE | REJECTED | BANNED; bỏ trống = tất cả")
            @RequestParam(required = false) ShopStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable oldestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("createdAt", "id"));
        return ApiResponse.ok(PageResponse.from(shopAdminService.list(status, oldestFirst)));
    }

    @Operation(summary = "Duyệt shop (PENDING → ACTIVE): seller bắt đầu đăng bán được")
    @PostMapping("/{id}/approve")
    ApiResponse<AdminShop> approve(@PathVariable Long id) {
        return ApiResponse.ok(shopAdminService.approve(id));
    }

    @Operation(summary = "Từ chối shop (PENDING → REJECTED) kèm lý do; seller sửa thông tin để gửi lại")
    @PostMapping("/{id}/reject")
    ApiResponse<AdminShop> reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok(shopAdminService.reject(id, req.reason()));
    }

    record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
