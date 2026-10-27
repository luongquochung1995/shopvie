package com.shopvi.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.common.config.CorsProperties;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.web.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Chống CSRF cho endpoint xác thực bằng cookie refresh ({@code /auth/refresh}, {@code /auth/logout}): ngoài cookie
 * SameSite=Strict, request có header {@code Origin} phải đến từ origin được phép (CORS). Request không có Origin
 * (curl, server-to-server) không mang cookie của trình duyệt nạn nhân nên không bị chặn.
 */
@Component
@RequiredArgsConstructor
class RefreshOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PATHS = Set.of(AuthController.BASE_PATH + "/refresh", AuthController.BASE_PATH + "/logout");

    private final CorsProperties cors;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !cors.allowedOrigins().contains(origin)) {
            response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of(ErrorCode.FORBIDDEN, "Origin not allowed", List.of()));
            return;
        }
        chain.doFilter(request, response);
    }
}
