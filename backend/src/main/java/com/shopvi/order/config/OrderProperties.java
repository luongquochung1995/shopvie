package com.shopvi.order.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình đơn hàng (prefix {@code app.order}).
 *
 * @param shippingFee phí vận chuyển cố định cho mỗi đơn (mỗi shop), VND — tạm thời cho tới khi tích hợp GHN (P4-5)
 */
@Validated
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(@PositiveOrZero long shippingFee) {

    @Configuration
    @EnableConfigurationProperties(OrderProperties.class)
    static class Config {
    }
}
