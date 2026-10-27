package com.shopvi.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access token JWT ký HS256 bằng {@code app.auth.jwt-secret}.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(AuthProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.jwtSecret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.auth.jwt-secret (JWT_SECRET) must be valid base64", ex);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.auth.jwt-secret (JWT_SECRET) must decode to at least 32 bytes; "
                    + "generate one with: openssl rand -base64 48");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer())));
        return decoder;
    }
}
