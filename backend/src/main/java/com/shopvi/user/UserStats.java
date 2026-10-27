package com.shopvi.user;

import java.util.Map;

/**
 * Thống kê người dùng cho trang tổng quan admin.
 *
 * @param byRole    số tài khoản theo vai trò (BUYER/SELLER/ADMIN)
 * @param newSince  số tài khoản đăng ký từ mốc thời gian được hỏi
 */
public record UserStats(long total, Map<String, Long> byRole, long locked, long newSince) {
}
