package com.shopvi.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.auth.application.JwtTokenService;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserRole;
import com.shopvi.user.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private final AuthProperties properties = new AuthProperties(SECRET, "shopvi", Duration.ofMinutes(15),
            Duration.ofDays(7), true);
    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSigningKey(properties);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);

    @Test
    void issuedTokenCarriesSubjectRoleIssuerAndExpiry() {
        Clock clock = Clock.systemUTC();
        var service = new JwtTokenService(config.jwtEncoder(key), properties, clock);

        var token = service.issue(new UserAccount(42L, "s@b.vn", "S", UserRole.SELLER, UserStatus.ACTIVE));
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("SELLER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("shopvi");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(token.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        var service = new JwtTokenService(config.jwtEncoder(key), properties, past);

        String token = service.issue(new UserAccount(1L, "a@b.vn", "A", UserRole.BUYER, UserStatus.ACTIVE)).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan256BitsFailsFast() {
        var weak = new AuthProperties(Base64.getEncoder().encodeToString(new byte[16]), "shopvi",
                Duration.ofMinutes(15), Duration.ofDays(7), true);

        assertThatThrownBy(() -> config.jwtSigningKey(weak)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
