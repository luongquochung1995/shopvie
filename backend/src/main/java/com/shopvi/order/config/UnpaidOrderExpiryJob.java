package com.shopvi.order.config;

import com.shopvi.order.application.OrderPaymentHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * P4-3: định kỳ hủy đơn thanh toán online quá hạn và hoàn kho. Chạy an toàn trên nhiều instance
 * (khóa dòng SKIP LOCKED + kiểm tra lại trạng thái trong transaction). Tắt bằng {@code app.order.expiry-job.enabled=false}.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.order.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
class UnpaidOrderExpiryJob {

    private final OrderPaymentHandler handler;

    @Scheduled(fixedDelayString = "${app.order.expiry-job.interval:60s}", initialDelayString = "30s")
    void run() {
        handler.expireOverdue();
    }
}
