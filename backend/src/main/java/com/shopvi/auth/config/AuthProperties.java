package com.shopvi.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình JWT & refresh token (prefix {@code app.auth}).
 *
 * @param jwtSecret         khóa HMAC dạng base64, tối thiểu 256 bit
 * @param issuer            claim {@code iss}
 * @param accessTokenTtl    thời hạn access token (mặc định 15 phút)
 * @param refreshTokenTtl   thời hạn refresh token (mặc định 7 ngày)
 * @param refreshCookieSecure gắn cờ Secure cho cookie refresh (bật ở production/HTTPS)
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean refreshCookieSecure) {
}
