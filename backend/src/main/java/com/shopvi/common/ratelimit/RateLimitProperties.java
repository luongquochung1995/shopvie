package com.shopvi.common.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hạn mức theo tên quy tắc: {@code capacity} lượt trong mỗi {@code period}.
 *
 * @param enabled tắt (vd. integration test) thì mọi request đều qua
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    public record Rule(long capacity, Duration period) {
    }
}
