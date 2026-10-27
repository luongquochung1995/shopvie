package com.shopvi.order.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.order.application.SellerDashboardService;
import com.shopvi.order.application.SellerDashboardService.Dashboard;
import com.shopvi.order.application.SellerDashboardService.Granularity;
import com.shopvi.order.application.SellerOrderService;
import com.shopvi.order.application.SellerOrderViews.SellerOrderDetail;
import com.shopvi.order.application.SellerOrderViews.SellerOrderSummary;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.domain.SellerAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Orders", description = "Đơn hàng của shop và dashboard doanh số (role SELLER)")
@RestController
@RequestMapping("/api/v1/seller")
@RequiredArgsConstructor
class SellerOrderController {

    private final SellerOrderService sellerOrderService;
    private final SellerDashboardService dashboardService;

    @Operation(summary = "Đơn hàng của shop (mới nhất trước), lọc theo trạng thái",
            description = "Mỗi đơn có actions = thao tác seller làm được ở trạng thái hiện tại.")
    @GetMapping("/orders")
    ApiResponse<PageResponse<SellerOrderSummary>> list(
            @CurrentUserId Long sellerId,
            @Parameter(description = "Lọc theo trạng thái; bỏ trống = tất cả") @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return ApiResponse.ok(PageResponse.from(sellerOrderService.list(sellerId, status, newestFirst)));
    }

    @Operation(summary = "Số đơn theo từng trạng thái (cho các tab)")
    @GetMapping("/orders/counts")
    ApiResponse<Map<OrderStatus, Long>> counts(@CurrentUserId Long sellerId) {
        return ApiResponse.ok(sellerOrderService.countByStatus(sellerId));
    }

    @Operation(summary = "Chi tiết đơn: sản phẩm, địa chỉ nhận hàng, lời nhắn, lịch sử trạng thái")
    @GetMapping("/orders/{code}")
    ApiResponse<SellerOrderDetail> detail(@CurrentUserId Long sellerId, @PathVariable String code) {
        return ApiResponse.ok(sellerOrderService.detail(sellerId, code));
    }

    @Operation(summary = "Xác nhận đơn (Chờ xác nhận → Chờ lấy hàng)")
    @PostMapping("/orders/{code}/confirm")
    ApiResponse<SellerOrderDetail> confirm(@CurrentUserId Long sellerId, @PathVariable String code,
                                           @Valid @RequestBody(required = false) NoteRequest req) {
        return perform(sellerId, code, SellerAction.CONFIRM, req == null ? null : req.note());
    }

    @Operation(summary = "Giao cho đơn vị vận chuyển (→ Đang giao)")
    @PostMapping("/orders/{code}/ship")
    ApiResponse<SellerOrderDetail> ship(@CurrentUserId Long sellerId, @PathVariable String code,
                                        @Valid @RequestBody(required = false) NoteRequest req) {
        return perform(sellerId, code, SellerAction.SHIP, req == null ? null : req.note());
    }

    @Operation(summary = "Xác nhận đã giao hàng (→ Đã giao); đơn COD chuyển sang đã thanh toán")
    @PostMapping("/orders/{code}/deliver")
    ApiResponse<SellerOrderDetail> deliver(@CurrentUserId Long sellerId, @PathVariable String code,
                                           @Valid @RequestBody(required = false) NoteRequest req) {
        return perform(sellerId, code, SellerAction.DELIVER, req == null ? null : req.note());
    }

    @Operation(summary = "Hủy đơn (chỉ khi chưa giao vận chuyển) kèm lý do; hoàn lại tồn kho")
    @PostMapping("/orders/{code}/cancel")
    ApiResponse<SellerOrderDetail> cancel(@CurrentUserId Long sellerId, @PathVariable String code,
                                          @Valid @RequestBody CancelRequest req) {
        return perform(sellerId, code, SellerAction.CANCEL, req.reason());
    }

    @Operation(summary = "Dashboard: doanh số theo ngày/tháng, số đơn theo trạng thái, top sản phẩm bán chạy",
            description = "Ngày theo giờ Việt Nam. Doanh số = tổng tiền hàng (không gồm phí ship) của đơn không bị "
                    + "hủy/trả hàng, theo ngày đặt. Mặc định: 30 ngày gần nhất (DAY) / 12 tháng gần nhất (MONTH); "
                    + "tối đa 366 ngày hoặc 36 tháng.")
    @GetMapping("/dashboard")
    ApiResponse<Dashboard> dashboard(
            @CurrentUserId Long sellerId,
            @Parameter(description = "Từ ngày (yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd), mặc định hôm nay") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "DAY | MONTH") @RequestParam(defaultValue = "DAY") Granularity granularity) {
        return ApiResponse.ok(dashboardService.dashboard(sellerId, from, to, granularity));
    }

    private ApiResponse<SellerOrderDetail> perform(Long sellerId, String code, SellerAction action, String note) {
        return ApiResponse.ok(sellerOrderService.perform(sellerId, code, action, note));
    }

    record NoteRequest(@Size(max = 500) String note) {
    }

    record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
