package com.shopvi.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.auth.config.AuthProperties;
import com.shopvi.auth.domain.RefreshToken;
import com.shopvi.auth.infra.RefreshTokenRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AuthProperties properties = new AuthProperties("secret", "shopvi", Duration.ofMinutes(15),
            Duration.ofDays(7), true);
    private final RefreshTokenService service =
            new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issueStoresOnlyHashWithExpiry() {
        String raw = service.issue(7L);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void rotateRevokesCurrentAndIssuesNew() {
        RefreshToken current = RefreshToken.issue(7L, RefreshTokenService.hash("old"), NOW.plusSeconds(60));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old"))).thenReturn(Optional.of(current));

        var rotation = service.rotate("old");

        assertThat(rotation.userId()).isEqualTo(7L);
        assertThat(rotation.newRawToken()).isNotEqualTo("old");
        assertThat(current.isRevoked()).isTrue();
    }

    @Test
    void reusingRevokedTokenRevokesAllSessions() {
        RefreshToken revoked = RefreshToken.issue(7L, RefreshTokenService.hash("old"), NOW.plusSeconds(60));
        revoked.revoke(NOW.minusSeconds(5));
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("old"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(repository).revokeAllOfUser(7L, NOW);
    }

    @Test
    void expiredTokenIsRejectedWithoutRevokingOthers() {
        RefreshToken expired = RefreshToken.issue(7L, RefreshTokenService.hash("old"), NOW);
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(BusinessException.class);
        verify(repository, never()).revokeAllOfUser(any(), any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
