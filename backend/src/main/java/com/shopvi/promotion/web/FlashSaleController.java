package com.shopvi.promotion.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.promotion.application.FlashSaleService;
import com.shopvi.promotion.application.FlashSaleService.FlashSaleCommand;
import com.shopvi.promotion.application.FlashSaleService.FlashSaleView;
import com.shopvi.promotion.application.FlashSaleService.ItemCommand;
import com.shopvi.promotion.application.FlashSaleService.PurchaseResult;
import com.shopvi.promotion.application.FlashSaleService.ReservationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Flash sale", description = "Phiên flash sale (admin), mua suất flash sale (người mua)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @Operation(summary = "Phiên flash sale đang chạy và sắp diễn ra (công khai)")
    @GetMapping("/flash-sales/current")
    ApiResponse<List<FlashSaleView>> current() {
        return ApiResponse.ok(flashSaleService.current());
    }

    @Operation(summary = "Mua suất flash sale",
            description = "Giữ suất ngay (nguyên tử trên Redis), đơn COD được tạo bất đồng bộ. Trả 202 + reservationId; "
                    + "poll GET /flash-sales/reservations/{id} tới khi CONFIRMED (có orderCode) hoặc FAILED.")
    @PostMapping("/flash-sales/items/{itemId}/purchase")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponse<PurchaseResult> purchase(@CurrentUserId Long userId, @PathVariable Long itemId,
                                         @Valid @RequestBody PurchaseRequest req) {
        return ApiResponse.ok(flashSaleService.purchase(userId, itemId, req.quantity(), req.addressId()));
    }

    @Operation(summary = "Trạng thái lượt giữ suất flash sale của tôi")
    @GetMapping("/flash-sales/reservations/{id}")
    ApiResponse<ReservationView> reservation(@CurrentUserId Long userId, @PathVariable UUID id) {
        return ApiResponse.ok(flashSaleService.reservation(userId, id));
    }

    // ----- Admin

    @Operation(summary = "Danh sách phiên flash sale (mới nhất trước)")
    @GetMapping("/admin/flash-sales")
    ApiResponse<PageResponse<FlashSaleView>> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(flashSaleService.list(pageable)));
    }

    @Operation(summary = "Tạo phiên flash sale", description = "Giá sale phải thấp hơn giá gốc; mỗi SKU một lần/phiên.")
    @PostMapping("/admin/flash-sales")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<FlashSaleView> create(@Valid @RequestBody FlashSaleRequest req) {
        return ApiResponse.ok(flashSaleService.create(req.toCommand()));
    }

    @Operation(summary = "Hủy phiên flash sale")
    @PostMapping("/admin/flash-sales/{id}/cancel")
    ApiResponse<FlashSaleView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(flashSaleService.cancel(id));
    }

    record PurchaseRequest(@Min(1) @Max(10) int quantity, Long addressId) {
    }

    record FlashSaleItemRequest(@NotNull Long variantId, @Positive long salePrice, @Min(1) @Max(100_000) int quota,
                                @Min(1) @Max(10) int perUserLimit) {
    }

    record FlashSaleRequest(@NotBlank @Size(max = 100) String name, @NotNull Instant startAt, @NotNull Instant endAt,
                            @NotEmpty @Size(max = 50) List<@Valid FlashSaleItemRequest> items) {

        FlashSaleCommand toCommand() {
            return new FlashSaleCommand(name, startAt, endAt, items.stream()
                    .map(i -> new ItemCommand(i.variantId(), i.salePrice(), i.quota(), i.perUserLimit())).toList());
        }
    }
}
