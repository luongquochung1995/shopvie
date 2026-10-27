package com.shopvi.order.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.promotion.FlashSaleApi;
import com.shopvi.promotion.FlashSaleOrderRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Tiêu thụ yêu cầu tạo đơn flash sale. Lỗi nghiệp vụ (hết kho, địa chỉ bị xóa...) → lượt giữ FAILED và suất được trả
 * lại; lỗi hạ tầng được ném lại để Kafka consumer thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FlashSaleOrderListener {

    static final String GROUP = "shopvi-flash-sale-orders";

    private final FlashSaleOrderService orderService;
    private final FlashSaleApi flashSaleApi;

    @KafkaListener(topics = FlashSaleOrderRequested.TOPIC, groupId = GROUP)
    void onRequested(FlashSaleOrderRequested req) {
        if (flashSaleApi.isProcessed(req.reservationId())) {
            return;
        }
        try {
            orderService.create(req);
        } catch (BusinessException ex) {
            log.info("Flash sale reservation {} failed: {}", req.reservationId(), ex.getErrorCode());
            flashSaleApi.failReservation(req, ex.getErrorCode().name());
        }
    }
}
