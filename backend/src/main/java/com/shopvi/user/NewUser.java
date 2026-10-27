package com.shopvi.user;

/**
 * Lệnh tạo tài khoản BUYER mới; mật khẩu đã được hash bởi module auth.
 */
public record NewUser(String email, String phone, String passwordHash, String fullName) {
}
