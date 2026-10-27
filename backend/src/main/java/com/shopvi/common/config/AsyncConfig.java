package com.shopvi.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Bật {@code @Async} (executor mặc định của Spring Boot, chạy trên virtual thread). */
@Configuration
@EnableAsync
class AsyncConfig {
}
