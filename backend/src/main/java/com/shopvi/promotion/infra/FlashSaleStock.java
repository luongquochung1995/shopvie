package com.shopvi.promotion.infra;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Quota flash sale đang bán trên Redis. Mỗi item có 3 key cùng hash tag {@code {itemId}}: {@code stock} (số còn lại),
 * {@code users} (hash userId → số đã giữ) và {@code meta} (start/end epoch millis, limit mỗi người). Giữ và trả quota
 * bằng script Lua nên kiểm tra + trừ là một bước nguyên tử — không bao giờ bán quá quota dù nhiều request đồng thời.
 */
@Component
@RequiredArgsConstructor
public class FlashSaleStock {

    public static final long SOLD_OUT = -1;
    public static final long LIMIT_REACHED = -2;
    public static final long NOT_ACTIVE = -3;
    public static final long NOT_LOADED = -4;

    private static final RedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -4 end
            local meta = redis.call('HMGET', KEYS[3], 'start', 'end', 'limit')
            if not meta[1] then return -4 end
            local now = tonumber(ARGV[3])
            if now < tonumber(meta[1]) or now >= tonumber(meta[2]) then return -3 end
            local qty = tonumber(ARGV[2])
            local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
            if bought + qty > tonumber(meta[3]) then return -2 end
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock < qty then return -1 end
            redis.call('DECRBY', KEYS[1], qty)
            redis.call('HINCRBY', KEYS[2], ARGV[1], qty)
            return stock - qty
            """, Long.class);

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('INCRBY', KEYS[1], ARGV[2])
            local left = redis.call('HINCRBY', KEYS[2], ARGV[1], -tonumber(ARGV[2]))
            if left <= 0 then redis.call('HDEL', KEYS[2], ARGV[1]) end
            return 1
            """, Long.class);

    private static final RedisScript<Long> RATE = new DefaultRedisScript<>("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * Giữ {@code quantity} suất cho người mua.
     *
     * @return số còn lại (≥ 0) hoặc một trong {@link #SOLD_OUT}, {@link #LIMIT_REACHED}, {@link #NOT_ACTIVE},
     *         {@link #NOT_LOADED}
     */
    public long reserve(Long itemId, Long userId, int quantity, Instant now) {
        Long result = redis.execute(RESERVE, keys(itemId), userId.toString(), Integer.toString(quantity),
                Long.toString(now.toEpochMilli()));
        return result == null ? NOT_LOADED : result;
    }

    /** Trả suất đã giữ (đơn không tạo được). Không làm gì nếu quota đã hết hạn khỏi Redis. */
    public void release(Long itemId, Long userId, int quantity) {
        redis.execute(RELEASE, keys(itemId), userId.toString(), Integer.toString(quantity));
    }

    /**
     * Nạp quota lên Redis nếu chưa có (SETNX nên không ghi đè số đang bán). Key hết hạn sau khi phiên kết thúc.
     *
     * @param boughtByUser số đã mua theo người (để giữ đúng giới hạn mỗi người khi nạp lại)
     */
    public void load(Long itemId, int remaining, int perUserLimit, Instant startAt, Instant endAt,
                     Map<Long, Integer> boughtByUser, Instant now) {
        Duration ttl = Duration.between(now, endAt).plusHours(1);
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        String stockKey = key(itemId, "stock");
        String metaKey = key(itemId, "meta");
        String usersKey = key(itemId, "users");
        redis.opsForHash().putAll(metaKey, Map.of("start", Long.toString(startAt.toEpochMilli()),
                "end", Long.toString(endAt.toEpochMilli()), "limit", Integer.toString(perUserLimit)));
        redis.expire(metaKey, ttl);
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(stockKey, Integer.toString(remaining), ttl))) {
            redis.delete(usersKey);
            if (!boughtByUser.isEmpty()) {
                Map<String, String> users = new HashMap<>();
                boughtByUser.forEach((u, q) -> users.put(u.toString(), q.toString()));
                redis.opsForHash().putAll(usersKey, users);
                redis.expire(usersKey, ttl);
            }
        }
    }

    /** Gỡ quota khỏi Redis (phiên bị hủy): lượt mua sau sẽ nạp lại từ DB và bị từ chối vì phiên không chạy. */
    public void unload(Long itemId) {
        redis.delete(keys(itemId));
    }

    /** Số suất còn lại trên Redis, {@code null} nếu chưa nạp. */
    public Integer remaining(Long itemId) {
        String value = redis.opsForValue().get(key(itemId, "stock"));
        return value == null ? null : Integer.valueOf(value);
    }

    /**
     * Đếm request của người mua trong cửa sổ cố định.
     *
     * @return {@code true} nếu còn trong giới hạn
     */
    public boolean tryAcquire(Long userId, int maxRequests, Duration window) {
        Long n = redis.execute(RATE, List.of("rl:fs:" + userId), Long.toString(window.toMillis()));
        return n != null && n <= maxRequests;
    }

    /** Trạng thái lượt giữ hàng (JSON) để client poll. */
    public void putReservation(UUID id, String json, Duration ttl) {
        redis.opsForValue().set("fs:res:" + id, json, ttl);
    }

    public String getReservation(UUID id) {
        return redis.opsForValue().get("fs:res:" + id);
    }

    private static List<String> keys(Long itemId) {
        return List.of(key(itemId, "stock"), key(itemId, "users"), key(itemId, "meta"));
    }

    private static String key(Long itemId, String suffix) {
        return "fs:{" + itemId + "}:" + suffix;
    }
}
