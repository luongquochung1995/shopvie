package com.shopvi.promotion.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.promotion.application.BannerService;
import com.shopvi.promotion.application.BannerService.BannerView;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
class PromotionCacheConfig {

    /** Banner trang chủ: TTL 1 phút (banner có khung giờ hiển thị), xóa ngay khi admin sửa. */
    @Bean
    RedisCacheManagerBuilderCustomizer promotionCacheCustomizer(ObjectMapper objectMapper,
                                                                @Value("${app.cache.key-prefix:shopvi:}") String prefix) {
        JavaType listOfBanners = objectMapper.getTypeFactory().constructCollectionType(List.class, BannerView.class);
        return builder -> builder.withCacheConfiguration(BannerService.CACHE, RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(prefix)
                .entryTtl(Duration.ofMinutes(1))
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, listOfBanners))));
    }
}
