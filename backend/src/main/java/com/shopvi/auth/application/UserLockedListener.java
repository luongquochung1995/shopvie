package com.shopvi.auth.application;

import com.shopvi.user.UserLocked;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Tài khoản bị khóa: thu hồi mọi refresh token ngay trong transaction khóa (access token còn hạn tối đa 15 phút). */
@Component
@RequiredArgsConstructor
class UserLockedListener {

    private final RefreshTokenService refreshTokenService;

    @EventListener
    void on(UserLocked event) {
        refreshTokenService.revokeAll(event.userId());
    }
}
