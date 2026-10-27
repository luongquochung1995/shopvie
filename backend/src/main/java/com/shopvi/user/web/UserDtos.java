package com.shopvi.user.web;

import com.shopvi.user.UserRole;
import com.shopvi.user.domain.Address;
import com.shopvi.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Request/response của module user. */
final class UserDtos {

    /** SĐT Việt Nam: 0xxxxxxxxx hoặc +84xxxxxxxxx. */
    static final String VN_PHONE = "^(0|\\+84)\\d{9,10}$";

    private UserDtos() {
    }

    record ProfileResponse(Long id, String email, String phone, String fullName, String avatarUrl, UserRole role,
                           Instant createdAt) {
        static ProfileResponse from(User u) {
            return new ProfileResponse(u.getId(), u.getEmail(), u.getPhone(), u.getFullName(), u.getAvatarUrl(),
                    u.getRole(), u.getCreatedAt());
        }
    }

    record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            @Pattern(regexp = VN_PHONE, message = "must be a valid Vietnamese phone number") String phone,
            @Size(max = 500) String avatarUrl) {
    }

    record AddressRequest(
            @NotBlank @Size(max = 100) String receiverName,
            @NotBlank @Pattern(regexp = VN_PHONE, message = "must be a valid Vietnamese phone number") String phone,
            @NotBlank @Size(max = 10) String provinceCode,
            // Tùy chọn: địa giới 2 cấp (tỉnh → xã) từ 07/2025 không còn cấp huyện
            @Size(max = 10) String districtCode,
            @NotBlank @Size(max = 10) String wardCode,
            @NotBlank @Size(max = 255) String detail,
            boolean isDefault) {

        Address.AddressData toData() {
            return new Address.AddressData(receiverName.trim(), phone.trim(), provinceCode,
                    districtCode == null || districtCode.isBlank() ? null : districtCode, wardCode, detail.trim());
        }
    }

    record AddressResponse(Long id, String receiverName, String phone, String provinceCode, String districtCode,
                           String wardCode, String detail, boolean isDefault) {
        static AddressResponse from(Address a) {
            return new AddressResponse(a.getId(), a.getReceiverName(), a.getPhone(), a.getProvinceCode(),
                    a.getDistrictCode(), a.getWardCode(), a.getDetail(), a.isDefault());
        }
    }
}
