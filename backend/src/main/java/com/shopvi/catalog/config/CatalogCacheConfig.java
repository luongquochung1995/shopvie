package com.shopvi.catalog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.catalog.application.CategoryQueryService;
import com.shopvi.catalog.application.CategoryTree;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
class CatalogCacheConfig {

    /**
     * Cache cây danh mục dạng JSON có kiểu cố định (không dùng default typing của Jackson).
     * transactionAware: lệnh evict chỉ chạy sau khi transaction ghi DB commit.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer catalogCacheCustomizer(ObjectMapper objectMapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, CategoryTree.class);
        return builder -> builder
                .transactionAware()
                .withCacheConfiguration(CategoryQueryService.CACHE, RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .serializeValuesWith(SerializationPair.fromSerializer(serializer)));
    }
}
