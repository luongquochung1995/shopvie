package com.shopvi.user;

/**
 * Thông tin tài khoản công khai cho các module khác (không chứa mật khẩu).
 */
public record UserAccount(Long id, String email, String fullName, UserRole role, UserStatus status) {

    public boolean isLocked() {
        return status == UserStatus.LOCKED;
    }
}
