package com.shopvi.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Đẩy tin tới hàng đợi riêng của user ({@code /user/queue/<queue>}) sau khi transaction hiện tại commit. */
@Slf4j
@Component
@RequiredArgsConstructor
class RealtimePublisher {

    static final String NOTIFICATIONS = "notifications";
    static final String CHAT = "chat";

    private final SimpMessagingTemplate template;

    void toUser(Long userId, String queue, Object payload) {
        Runnable send = () -> {
            try {
                template.convertAndSendToUser(userId.toString(), "/queue/" + queue, payload);
            } catch (RuntimeException ex) {
                // Không có kết nối / lỗi broker: dữ liệu vẫn đã lưu, client tải lại khi kết nối
                log.warn("Could not push {} message to user {}: {}", queue, userId, ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
