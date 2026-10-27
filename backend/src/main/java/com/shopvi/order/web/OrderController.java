package com.shopvi.order.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.order.application.OrderService;
import com.shopvi.order.application.OrderViews.OrderDetail;
import com.shopvi.order.application.OrderViews.OrderSummary;
import com.shopvi.order.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "Orders", description = "Đơn mua của người dùng đang đăng nhập")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Danh sách đơn mua (mới nhất trước), lọc theo trạng thái")
    @GetMapping
    ApiResponse<PageResponse<OrderSummary>> list(
            @CurrentUserId Long userId,
            @Parameter(description = "Lọc theo trạng thái; bỏ trống = tất cả") @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.from(orderService.list(userId, status, newestFirst)));
    }

    @Operation(summary = "Chi tiết đơn: sản phẩm, địa chỉ (snapshot), lịch sử trạng thái")
    @GetMapping("/{code}")
    ApiResponse<OrderDetail> detail(@CurrentUserId Long userId, @PathVariable String code) {
        return ApiResponse.ok(orderService.detail(userId, code));
    }

    @Operation(summary = "Hủy đơn (chỉ khi Chờ xác nhận / Đã xác nhận); hoàn lại tồn kho")
    @PostMapping("/{code}/cancel")
    ApiResponse<OrderDetail> cancel(@CurrentUserId Long userId, @PathVariable String code,
                                    @Valid @RequestBody(required = false) CancelRequest req) {
        return ApiResponse.ok(orderService.cancel(userId, code, req == null ? null : req.reason()));
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }
}
