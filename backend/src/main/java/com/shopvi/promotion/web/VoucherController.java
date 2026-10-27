package com.shopvi.promotion.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.promotion.application.VoucherService;
import com.shopvi.promotion.application.VoucherService.VoucherCommand;
import com.shopvi.promotion.application.VoucherService.VoucherView;
import com.shopvi.promotion.domain.Voucher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Vouchers", description = "Voucher sàn (admin), voucher shop (seller), voucher dùng được khi thanh toán")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "Voucher đang chạy mà người mua còn dùng được (voucher sàn + voucher các shop trong giỏ)")
    @GetMapping("/vouchers/available")
    ApiResponse<List<VoucherView>> available(
            @CurrentUserId Long userId,
            @Parameter(description = "Id các shop trong giỏ, cách nhau bởi dấu phẩy") @RequestParam(defaultValue = "")
            List<Long> shopIds) {
        return ApiResponse.ok(voucherService.available(userId, shopIds));
    }

    // ----- Seller

    @Operation(summary = "Voucher của shop (role SELLER), mới nhất trước")
    @GetMapping("/seller/vouchers")
    ApiResponse<PageResponse<VoucherView>> shopVouchers(@CurrentUserId Long sellerId,
                                                        @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(voucherService.listOfShop(sellerId, newestFirst(pageable))));
    }

    @Operation(summary = "Tạo voucher shop", description = "Mã 4-20 ký tự A-Z, 0-9, duy nhất toàn sàn. PERCENT: value = 1-100.")
    @PostMapping("/seller/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<VoucherView> createShopVoucher(@CurrentUserId Long sellerId, @Valid @RequestBody VoucherRequest req) {
        return ApiResponse.ok(voucherService.createForShop(sellerId, req.toCommand()));
    }

    @Operation(summary = "Ngừng voucher shop")
    @PostMapping("/seller/vouchers/{id}/disable")
    ApiResponse<VoucherView> disableShopVoucher(@CurrentUserId Long sellerId, @PathVariable Long id) {
        return ApiResponse.ok(voucherService.disableOfShop(sellerId, id));
    }

    // ----- Admin

    @Operation(summary = "Voucher sàn (role ADMIN), mới nhất trước")
    @GetMapping("/admin/vouchers")
    ApiResponse<PageResponse<VoucherView>> platformVouchers(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(voucherService.listPlatform(newestFirst(pageable))));
    }

    @Operation(summary = "Tạo voucher sàn (áp cho mọi shop)")
    @PostMapping("/admin/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<VoucherView> createPlatformVoucher(@Valid @RequestBody VoucherRequest req) {
        return ApiResponse.ok(voucherService.createPlatform(req.toCommand()));
    }

    @Operation(summary = "Ngừng voucher sàn")
    @PostMapping("/admin/vouchers/{id}/disable")
    ApiResponse<VoucherView> disablePlatformVoucher(@PathVariable Long id) {
        return ApiResponse.ok(voucherService.disablePlatform(id));
    }

    private static Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
    }

    record VoucherRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "4-20 letters or digits") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Voucher.Type type,
            @Positive @Max(1_000_000_000) long value,
            @PositiveOrZero long minOrderValue,
            @Positive Long maxDiscount,
            @Min(1) @Max(1_000_000) int usageLimit,
            @Min(1) @Max(100) int perUserLimit,
            @NotNull Instant startAt,
            @NotNull Instant endAt) {

        VoucherCommand toCommand() {
            return new VoucherCommand(code, name, type, value, minOrderValue, maxDiscount, usageLimit, perUserLimit,
                    startAt, endAt);
        }
    }
}
