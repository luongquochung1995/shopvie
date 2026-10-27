package com.shopvi.auth.application;

import com.shopvi.auth.application.JwtTokenService.AccessToken;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.user.NewUser;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import com.shopvi.user.UserCredentials;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    /** Hash giả để so khớp khi email không tồn tại → thời gian phản hồi như nhau, khó dò email. */
    private final String dummyHash;

    public AuthService(UserApi userApi, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService) {
        this.userApi = userApi;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResult register(String email, String password, String fullName, String phone) {
        UserAccount account = userApi.create(new NewUser(email, phone, passwordEncoder.encode(password), fullName));
        return issueTokens(account);
    }

    @Transactional
    public AuthResult login(String email, String password) {
        Optional<UserCredentials> credentials = userApi.findCredentialsByEmail(email);
        if (credentials.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(password, credentials.get().passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        UserAccount account = credentials.get().account();
        if (account.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        return issueTokens(account);
    }

    /** Đổi refresh token cũ lấy cặp token mới. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        UserAccount account = userApi.findById(rotation.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (account.isLocked()) {
            refreshTokenService.revokeAll(account.id());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        return new AuthResult(account, jwtTokenService.issue(account), rotation.newRawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private AuthResult issueTokens(UserAccount account) {
        return new AuthResult(account, jwtTokenService.issue(account), refreshTokenService.issue(account.id()));
    }

    public record AuthResult(UserAccount account, AccessToken accessToken, String refreshToken) {
    }
}
