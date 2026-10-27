package com.shopvi.user;

/**
 * Tài khoản kèm hash mật khẩu — chỉ dùng cho module auth khi xác thực đăng nhập.
 */
public record UserCredentials(UserAccount account, String passwordHash) {
}
