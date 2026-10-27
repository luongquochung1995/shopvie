package com.shopvi.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.auth.application.JwtTokenService.AccessToken;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.NewUser;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import com.shopvi.user.UserCredentials;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final UserAccount ACTIVE = new UserAccount(1L, "a@b.vn", "A", UserRole.BUYER, UserStatus.ACTIVE);
    private static final UserAccount LOCKED = new UserAccount(2L, "l@b.vn", "L", UserRole.BUYER, UserStatus.LOCKED);

    private final UserApi userApi = mock(UserApi.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwt = mock(JwtTokenService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("hashed");
        when(jwt.issue(any())).thenReturn(new AccessToken("access", 900));
        when(refreshTokens.issue(any())).thenReturn("refresh");
        service = new AuthService(userApi, encoder, jwt, refreshTokens);
    }

    @Test
    void registerHashesPasswordAndIssuesTokens() {
        when(userApi.create(any())).thenReturn(ACTIVE);

        var result = service.register("a@b.vn", "secret123", "A", null);

        verify(userApi).create(new NewUser("a@b.vn", null, "hashed", "A"));
        assertThat(result.accessToken().value()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void loginUnknownEmailStillChecksPasswordAgainstDummyHash() {
        when(userApi.findCredentialsByEmail("x@b.vn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("x@b.vn", "pw"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(encoder).matches(eq("pw"), anyString());
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void loginWrongPassword() {
        when(userApi.findCredentialsByEmail("a@b.vn")).thenReturn(Optional.of(new UserCredentials(ACTIVE, "h")));
        when(encoder.matches("bad", "h")).thenReturn(false);

        assertThatThrownBy(() -> service.login("a@b.vn", "bad"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void loginLockedAccountOnlyAfterCorrectPassword() {
        when(userApi.findCredentialsByEmail("l@b.vn")).thenReturn(Optional.of(new UserCredentials(LOCKED, "h")));
        when(encoder.matches("ok", "h")).thenReturn(true);

        assertThatThrownBy(() -> service.login("l@b.vn", "ok"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        verify(refreshTokens, never()).issue(any());
    }

    @Test
    void loginSuccess() {
        when(userApi.findCredentialsByEmail("a@b.vn")).thenReturn(Optional.of(new UserCredentials(ACTIVE, "h")));
        when(encoder.matches("ok", "h")).thenReturn(true);

        assertThat(service.login("a@b.vn", "ok").account()).isEqualTo(ACTIVE);
    }

    @Test
    void refreshForLockedUserRevokesAllSessions() {
        when(refreshTokens.rotate("r")).thenReturn(new RefreshTokenService.Rotation(2L, "r2"));
        when(userApi.findById(2L)).thenReturn(Optional.of(LOCKED));

        assertThatThrownBy(() -> service.refresh("r"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        verify(refreshTokens).revokeAll(2L);
    }

    @Test
    void refreshWithoutTokenIsRejected() {
        assertThatThrownBy(() -> service.refresh(" "))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshReturnsRotatedToken() {
        when(refreshTokens.rotate("r")).thenReturn(new RefreshTokenService.Rotation(1L, "r2"));
        when(userApi.findById(1L)).thenReturn(Optional.of(ACTIVE));

        var result = service.refresh("r");

        assertThat(result.refreshToken()).isEqualTo("r2");
        assertThat(result.accessToken().value()).isEqualTo("access");
    }
}
