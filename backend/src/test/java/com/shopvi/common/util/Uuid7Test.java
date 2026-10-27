package com.shopvi.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Uuid7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = Uuid7.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTimestampSoIdsSortByCreationTime() {
        UUID earlier = Uuid7.next(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        UUID later = Uuid7.next(Clock.fixed(Instant.parse("2026-01-01T00:00:00.001Z"), ZoneOffset.UTC));

        assertThat(earlier.getMostSignificantBits() >>> 16).isEqualTo(Instant.parse("2026-01-01T00:00:00Z").toEpochMilli());
        assertThat(earlier.toString()).isLessThan(later.toString());
    }

    @Test
    void isUnique() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Uuid7.next());
        }
        assertThat(ids).hasSize(10_000);
    }
}
