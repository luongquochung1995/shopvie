package com.shopvi.common.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Token bucket Bucket4j lưu trên Redis (compare-and-swap), dùng client đã cấu hình của Spring Data Redis.
 * Redis lỗi → cho request đi qua (fail-open) và ghi log: không để rate limit làm sập đăng nhập / thanh toán.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    static final String KEY_PREFIX = "rl:";

    private final LettuceConnectionFactory connectionFactory;
    private volatile ProxyManager<String> proxyManager;
    private volatile StatefulRedisConnection<String, byte[]> connection;

    /**
     * Lấy một lượt của {@code key} theo quy tắc {@code rule}.
     *
     * @return empty nếu được phép; nếu bị chặn: số giây cần chờ
     */
    public OptionalLong tryAcquire(String key, RateLimitProperties.Rule rule) {
        try {
            BucketConfiguration config = BucketConfiguration.builder()
                    .addLimit(limit -> limit.capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()))
                    .build();
            ConsumptionProbe probe = proxy().builder().build(KEY_PREFIX + key, () -> config).tryConsumeAndReturnRemaining(1);
            return probe.isConsumed() ? OptionalLong.empty()
                    : OptionalLong.of(Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds()));
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable, allowing request {}: {}", key, ex.getMessage());
            return OptionalLong.empty();
        }
    }

    private ProxyManager<String> proxy() {
        if (proxyManager == null) {
            synchronized (this) {
                if (proxyManager == null) {
                    RedisClient client = (RedisClient) connectionFactory.getNativeClient();
                    connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                    proxyManager = Bucket4jLettuce.casBasedBuilder(connection)
                            .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                    Duration.ofSeconds(10)))
                            .build();
                }
            }
        }
        return proxyManager;
    }

    @PreDestroy
    void close() {
        if (connection != null) {
            connection.close();
        }
    }
}
