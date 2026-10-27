package com.shopvi.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Inject {@link Clock} thay vì gọi {@code Instant.now()} trực tiếp để test được logic thời gian. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
