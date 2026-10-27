package com.shopvi.auth.application;

import com.shopvi.auth.config.AuthProperties;
import com.shopvi.auth.domain.RefreshToken;
import com.shopvi.auth.infra.RefreshTokenRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token dạng chuỗi ngẫu nhiên 256 bit, xoay vòng mỗi lần dùng.
 * Nếu một token đã bị thu hồi được dùng lại (dấu hiệu bị đánh cắp) → thu hồi toàn bộ phiên của user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    /** Tạo token mới cho user, trả về giá trị gốc (chỉ gửi cho client, không lưu). */
    @Transactional
    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(RefreshToken.issue(userId, hash(raw), clock.instant().plus(properties.refreshTokenTtl())));
        return raw;
    }

    /**
     * Thu hồi token cũ và cấp token mới. Không rollback khi ném lỗi để việc thu hồi hàng loạt
     * (phát hiện dùng lại token) vẫn được ghi xuống DB.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Rotation rotate(String raw) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHashForUpdate(hash(raw))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (current.isRevoked()) {
            log.warn("Revoked refresh token reused for user {} - revoking all sessions", current.getUserId());
            repository.revokeAllOfUser(current.getUserId(), now);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (current.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        current.revoke(now);
        return new Rotation(current.getUserId(), issue(current.getUserId()));
    }

    @Transactional
    public void revoke(String raw) {
        repository.findByTokenHash(hash(raw)).ifPresent(t -> t.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllOfUser(userId, clock.instant());
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record Rotation(Long userId, String newRawToken) {
    }
}
