package com.shopvi.common.ratelimit;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Áp {@link RateLimit} cho handler: khóa = tên quy tắc + IP (hoặc id user). Vượt hạn mức → 429 + Retry-After. */
@Component
@RequiredArgsConstructor
class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled() || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit annotation = method.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }
        RateLimitProperties.Rule rule = properties.rules().get(annotation.value());
        if (rule == null) {
            throw new IllegalStateException("Missing app.rate-limit.rules." + annotation.value());
        }
        OptionalLong retryAfter = limiter.tryAcquire(annotation.value() + ":" + subject(annotation.key(), request), rule);
        if (retryAfter.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.getAsLong()));
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    /** Id user (JWT sub) khi cần và đã đăng nhập; không thì IP (sau proxy: cấu hình server.forward-headers-strategy). */
    private static String subject(RateLimit.Key key, HttpServletRequest request) {
        if (key == RateLimit.Key.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                return "u" + jwt.getToken().getSubject();
            }
        }
        return "ip" + request.getRemoteAddr();
    }
}
