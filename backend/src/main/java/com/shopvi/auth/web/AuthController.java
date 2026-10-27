package com.shopvi.auth.web;

import com.shopvi.auth.application.AuthService;
import com.shopvi.auth.application.AuthService.AuthResult;
import com.shopvi.auth.config.AuthProperties;
import com.shopvi.auth.web.AuthDtos.AuthResponse;
import com.shopvi.auth.web.AuthDtos.LoginRequest;
import com.shopvi.auth.web.AuthDtos.RegisterRequest;
import com.shopvi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access token trả trong body; refresh token chỉ nằm trong cookie httpOnly (path /api/v1/auth, SameSite=Strict).
 */
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, làm mới và thu hồi token")
@RestController
@RequestMapping(AuthController.BASE_PATH)
@RequiredArgsConstructor
class AuthController {

    static final String BASE_PATH = "/api/v1/auth";
    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final AuthProperties properties;

    @Operation(summary = "Đăng ký tài khoản người mua và đăng nhập luôn")
    @PostMapping("/register")
    ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        AuthResult result = authService.register(req.email(), req.password(), req.fullName(), req.phone());
        return withRefreshCookie(HttpStatus.CREATED, result);
    }

    @Operation(summary = "Đăng nhập bằng email + mật khẩu")
    @PostMapping("/login")
    ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return withRefreshCookie(HttpStatus.OK, authService.login(req.email(), req.password()));
    }

    @Operation(summary = "Đổi refresh token (cookie) lấy access token mới; refresh token được xoay vòng")
    @PostMapping("/refresh")
    ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @Operation(summary = "Đăng xuất: thu hồi refresh token và xóa cookie")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(HttpStatus status, AuthResult result) {
        ResponseCookie cookie = refreshCookie(result.refreshToken(), properties.refreshTokenTtl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(AuthResponse.from(result)));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(BASE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
