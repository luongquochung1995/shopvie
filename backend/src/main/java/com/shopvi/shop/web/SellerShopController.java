package com.shopvi.shop.web;

import static com.shopvi.common.util.ValidationPatterns.HTTP_URL;
import static com.shopvi.common.util.ValidationPatterns.HTTP_URL_MESSAGE;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.application.SellerShopService;
import com.shopvi.shop.application.ShopViews.MyShop;
import com.shopvi.shop.application.ShopViews.ShopInfoCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller - Shop", description = "Đăng ký mở shop và thông tin shop của seller")
@RestController
@RequestMapping("/api/v1/seller/shop")
@RequiredArgsConstructor
class SellerShopController {

    private final SellerShopService sellerShopService;

    @Operation(summary = "Đăng ký mở shop (user đã đăng nhập, chưa có shop)",
            description = "Tạo shop PENDING chờ admin duyệt và nâng tài khoản BUYER → SELLER. "
                    + "Gọi POST /auth/refresh sau đó để nhận access token mang role mới.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MyShop> register(@CurrentUserId Long userId, @Valid @RequestBody ShopInfoRequest req) {
        return ApiResponse.ok(sellerShopService.register(userId, req.toCommand()));
    }

    @Operation(summary = "Shop của tôi (kèm trạng thái duyệt, lý do từ chối)")
    @GetMapping
    ApiResponse<MyShop> myShop(@CurrentUserId Long userId) {
        return ApiResponse.ok(sellerShopService.myShop(userId));
    }

    @Operation(summary = "Sửa thông tin shop; shop bị từ chối sẽ được gửi duyệt lại (REJECTED → PENDING)")
    @PutMapping
    ApiResponse<MyShop> update(@CurrentUserId Long userId, @Valid @RequestBody ShopInfoRequest req) {
        return ApiResponse.ok(sellerShopService.update(userId, req.toCommand()));
    }

    @Operation(summary = "Cập nhật địa chỉ lấy hàng (dùng tính phí ship và tạo vận đơn)")
    @PutMapping("/pickup-address")
    ApiResponse<MyShop> updatePickupAddress(@CurrentUserId Long userId, @Valid @RequestBody PickupAddressRequest req) {
        return ApiResponse.ok(sellerShopService.updatePickupAddress(userId,
                new PickupAddress(req.name(), req.phone(), req.provinceCode(), req.wardCode(), req.detail())));
    }

    record PickupAddressRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^(0|\\+84)\\d{9,10}$", message = "must be a valid Vietnamese phone number")
            String phone,
            @NotBlank @Size(max = 10) String provinceCode,
            @NotBlank @Size(max = 10) String wardCode,
            @NotBlank @Size(max = 255) String detail) {
    }

    record ShopInfoRequest(
            @NotBlank @Size(min = 3, max = 100) String name,
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = HTTP_URL, message = HTTP_URL_MESSAGE) String logoUrl) {

        ShopInfoCommand toCommand() {
            return new ShopInfoCommand(name, description, logoUrl);
        }
    }
}
