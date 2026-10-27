package com.shopvi.user.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.user.application.AddressService;
import com.shopvi.user.application.UserService;
import com.shopvi.user.web.UserDtos.AddressRequest;
import com.shopvi.user.web.UserDtos.AddressResponse;
import com.shopvi.user.web.UserDtos.ProfileResponse;
import com.shopvi.user.web.UserDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me", description = "Hồ sơ và sổ địa chỉ của người dùng đang đăng nhập")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
class MeController {

    private final UserService userService;
    private final AddressService addressService;

    @Operation(summary = "Lấy hồ sơ người dùng hiện tại")
    @GetMapping
    ApiResponse<ProfileResponse> me(@CurrentUserId Long userId) {
        return ApiResponse.ok(ProfileResponse.from(userService.getProfile(userId)));
    }

    @Operation(summary = "Cập nhật hồ sơ (họ tên, SĐT, avatar)")
    @PutMapping
    ApiResponse<ProfileResponse> update(@CurrentUserId Long userId, @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(ProfileResponse.from(
                userService.updateProfile(userId, req.fullName(), req.phone(), req.avatarUrl())));
    }

    @Operation(summary = "Danh sách địa chỉ (mặc định đứng đầu)")
    @GetMapping("/addresses")
    ApiResponse<List<AddressResponse>> addresses(@CurrentUserId Long userId) {
        return ApiResponse.ok(addressService.list(userId).stream().map(AddressResponse::from).toList());
    }

    @Operation(summary = "Thêm địa chỉ; địa chỉ đầu tiên tự động là mặc định")
    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AddressResponse> createAddress(@CurrentUserId Long userId, @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(AddressResponse.from(addressService.create(userId, req.toData(), req.isDefault())));
    }

    @Operation(summary = "Sửa địa chỉ")
    @PutMapping("/addresses/{id}")
    ApiResponse<AddressResponse> updateAddress(@CurrentUserId Long userId, @PathVariable Long id,
                                               @Valid @RequestBody AddressRequest req) {
        return ApiResponse.ok(AddressResponse.from(addressService.update(userId, id, req.toData(), req.isDefault())));
    }

    @Operation(summary = "Đặt làm địa chỉ mặc định")
    @PatchMapping("/addresses/{id}/default")
    ApiResponse<AddressResponse> makeDefault(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok(AddressResponse.from(addressService.makeDefault(userId, id)));
    }

    @Operation(summary = "Xóa địa chỉ; nếu là mặc định thì địa chỉ mới nhất còn lại thành mặc định")
    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@CurrentUserId Long userId, @PathVariable Long id) {
        addressService.delete(userId, id);
    }
}
