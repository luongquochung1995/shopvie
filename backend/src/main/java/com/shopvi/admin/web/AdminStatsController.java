package com.shopvi.admin.web;

import com.shopvi.admin.application.AdminStatsService;
import com.shopvi.admin.application.AdminStatsService.Overview;
import com.shopvi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Stats", description = "Thống kê tổng quan toàn sàn (role ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
class AdminStatsController {

    private final AdminStatsService statsService;

    @Operation(summary = "Tổng quan: người dùng, shop, sản phẩm theo trạng thái; đơn hàng + GMV từng ngày trong N ngày")
    @GetMapping
    ApiResponse<Overview> overview(@RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return ApiResponse.ok(statsService.overview(days));
    }
}
