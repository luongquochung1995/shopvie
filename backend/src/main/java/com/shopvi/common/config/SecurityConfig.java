package com.shopvi.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.security.Roles;
import com.shopvi.common.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Stateless API bảo vệ bằng JWT access token (Bearer). {@code JwtDecoder} do module {@code auth} cung cấp.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHORIZED);
        AccessDeniedHandler deniedHandler = (req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN);

        http
                // API stateless dùng Bearer token; cookie refresh token có SameSite=Strict (CSRF rà soát ở P7-4)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // P7-4: API không được nhúng frame, không lộ URL qua Referer (các header mặc định khác giữ nguyên)
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Prometheus scrape: prod chạy trên cổng quản trị riêng (management.server.port), chỉ mở trong mạng nội bộ
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Bắt tay WebSocket/SockJS: xác thực bằng JWT trong khung STOMP CONNECT (StompAuthInterceptor)
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(API + "/auth/**").permitAll()
                        // Cổng thanh toán / hãng vận chuyển gọi về: xác thực bằng chữ ký / token riêng
                        .requestMatchers(API + "/payments/vnpay/**", API + "/payments/momo/**",
                                API + "/shipping/ghn/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/products/**", API + "/categories/**",
                                API + "/shops/**", API + "/search/**", API + "/flash-sales/current",
                                API + "/banners", API + "/seo/**").permitAll()
                        // Mở shop: người mua đã đăng nhập (service chặn ADMIN và người đã có shop)
                        .requestMatchers(HttpMethod.POST, API + "/seller/shop/register").authenticated()
                        .requestMatchers(API + "/seller/**").hasRole(Roles.SELLER)
                        .requestMatchers(API + "/admin/**").hasRole(Roles.ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /** Claim {@code role} (BUYER|SELLER|ADMIN) → authority {@code ROLE_<role>}. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(Roles.CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code));
    }
}
