package com.shopvi.common.util;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * UUID phiên bản 7 (RFC 9562): 48 bit thời gian (ms) + bit ngẫu nhiên → sắp xếp được theo thời gian tạo,
 * dùng làm mã public-facing (mã đơn hàng, checkout id) thay vì lộ id tự tăng.
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID next(Clock clock) {
        long millis = clock.millis();
        long randA = RANDOM.nextLong();
        long randB = RANDOM.nextLong();
        long msb = (millis << 16) | 0x7000L | (randA & 0x0FFFL);
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    public static UUID next() {
        return next(Clock.systemUTC());
    }
}
