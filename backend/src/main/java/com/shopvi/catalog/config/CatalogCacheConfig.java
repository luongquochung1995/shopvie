package com.shopvi.catalog.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.catalog.application.CategoryQueryService;
import com.shopvi.catalog.application.CategoryTree;
import com.shopvi.catalog.application.ProductQueryService;
import com.shopvi.catalog.application.ProductViews.ProductCard;
import com.shopvi.catalog.application.ProductViews.ProductDetail;
import com.shopvi.common.web.PageResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
class CatalogCacheConfig {

    /**
     * Cache JSON có kiểu cố định (không dùng default typing của Jackson): cây danh mục, chi tiết sản phẩm (evict khi
     * sản phẩm đổi), trang danh sách sản phẩm (TTL ngắn — số đã bán / điểm có thể trễ tối đa 1 phút).
     * transactionAware: lệnh evict chỉ chạy sau khi transaction ghi DB commit. Khóa có tiền tố theo môi trường để
     * dev và integration test dùng chung Redis không lẫn dữ liệu.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer catalogCacheCustomizer(ObjectMapper objectMapper,
                                                              @Value("${app.cache.key-prefix:shopvi:}") String prefix) {
        JavaType pageOfCards = objectMapper.getTypeFactory().constructParametricType(PageResponse.class, ProductCard.class);
        return builder -> builder
                .transactionAware()
                .withCacheConfiguration(CategoryQueryService.CACHE, config(prefix, Duration.ofHours(1),
                        new Jackson2JsonRedisSerializer<>(objectMapper, CategoryTree.class)))
                .withCacheConfiguration(ProductQueryService.DETAIL_CACHE, config(prefix, Duration.ofMinutes(10),
                        new Jackson2JsonRedisSerializer<>(objectMapper, ProductDetail.class)))
                .withCacheConfiguration(ProductQueryService.LIST_CACHE, config(prefix, Duration.ofMinutes(1),
                        new Jackson2JsonRedisSerializer<>(objectMapper, pageOfCards)));
    }

    private static RedisCacheConfiguration config(String prefix, Duration ttl, Jackson2JsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(prefix)
                .entryTtl(ttl)
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
