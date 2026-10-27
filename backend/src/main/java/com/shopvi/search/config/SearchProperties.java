package com.shopvi.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Cấu hình tìm kiếm (prefix {@code app.search}).
 *
 * @param enabled          false = không đồng bộ chỉ mục, API tìm kiếm trả 503 (vd. môi trường không có Elasticsearch)
 * @param alias            alias của chỉ mục sản phẩm; mỗi lần reindex tạo chỉ mục mới rồi chuyển alias sang
 * @param reindexOnStartup tạo chỉ mục + nạp toàn bộ sản phẩm khi khởi động nếu alias chưa tồn tại
 */
@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(boolean enabled, String alias, boolean reindexOnStartup) {

    public SearchProperties {
        alias = StringUtils.hasText(alias) ? alias : "products";
    }

    @Configuration
    @EnableConfigurationProperties(SearchProperties.class)
    static class Config {
    }
}
