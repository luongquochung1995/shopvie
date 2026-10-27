package com.shopvi.user;

/** Sự kiện: admin khóa tài khoản — các phiên đăng nhập đang có phải bị thu hồi. */
public record UserLocked(Long userId) {
}
