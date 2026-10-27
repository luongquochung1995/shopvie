package com.shopvi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@IntegrationTest
class AuthFlowIT {

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void registerLoginRefreshLogout() {
        String email = "Flow-" + UUID.randomUUID() + "@Example.com";
        var register = api.post("/auth/register", null,
                Map.of("email", email, "password", "secret123", "fullName", "Nguyễn Văn A"));
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getBody().at("/data/user/email").asText()).isEqualTo(email.toLowerCase());
        assertThat(register.getBody().at("/data/user/role").asText()).isEqualTo("BUYER");
        String cookie = refreshCookie(register);
        assertThat(setCookie(register)).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");

        // Access token dùng được cho /me
        String accessToken = register.getBody().at("/data/accessToken").asText();
        var me = api.get("/me", accessToken);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().at("/data/fullName").asText()).isEqualTo("Nguyễn Văn A");

        // Refresh → token mới, cookie xoay vòng
        var refreshed = refresh(cookie);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotated = refreshCookie(refreshed);
        assertThat(rotated).isNotEqualTo(cookie);

        // Dùng lại token cũ → bị từ chối và toàn bộ phiên bị thu hồi (kể cả token vừa xoay)
        assertThat(refresh(cookie).getBody().at("/error/code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
        assertThat(refresh(rotated).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Đăng nhập lại (email không phân biệt hoa thường) rồi đăng xuất
        var login = api.post("/auth/login", null, Map.of("email", email.toUpperCase(), "password", "secret123"));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String loginCookie = refreshCookie(login);
        var logout = api.exchange(HttpMethod.POST, "/auth/logout", null, null, Map.of(HttpHeaders.COOKIE, loginCookie));
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(setCookie(logout)).contains("Max-Age=0");
        assertThat(refresh(loginCookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void duplicateEmailIsConflict() {
        var response = api.post("/auth/register", null,
                Map.of("email", "BUYER@shopvi.local", "password", "secret123", "fullName", "Dup"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().at("/error/code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() {
        var response = api.post("/auth/register", null, Map.of("email", "not-an-email", "password", "123",
                "fullName", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<String> fields = response.getBody().at("/error/details").findValuesAsText("field");
        assertThat(fields).containsExactlyInAnyOrder("email", "password", "fullName");
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        var wrongPassword = api.post("/auth/login", null, Map.of("email", "buyer@shopvi.local", "password", "nope1234"));
        var unknownEmail = api.post("/auth/login", null, Map.of("email", "ghost@shopvi.local", "password", "nope1234"));

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getBody()).isEqualTo(wrongPassword.getBody());
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        var response = api.post("/auth/refresh", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().at("/error/code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void tamperedAccessTokenIsRejected() {
        String token = api.login("buyer@shopvi.local");
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(api.get("/me", tampered).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> refresh(String cookie) {
        return api.exchange(HttpMethod.POST, "/auth/refresh", null, null, Map.of(HttpHeaders.COOKIE, cookie));
    }

    private static String setCookie(ResponseEntity<?> response) {
        return String.join(";", response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
    }

    /** Giá trị "refresh_token=..." từ Set-Cookie, dùng làm header Cookie cho request sau. */
    private static String refreshCookie(ResponseEntity<?> response) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.substring(0, c.indexOf(';')))
                .findFirst().orElseThrow();
    }
}
