package com.shopvi.notification.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.notification.domain.Notification;
import com.shopvi.notification.domain.NotificationType;
import com.shopvi.notification.infra.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lưu thông báo vào bảng {@code notifications} và đẩy realtime tới {@code /user/queue/notifications}. */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository repository;
    private final RealtimePublisher publisher;
    private final Clock clock;

    public NotificationView notify(Long userId, NotificationType type, Map<String, Object> params, String link) {
        Notification n = repository.save(Notification.of(userId, type, params, link));
        NotificationView view = NotificationView.of(n);
        publisher.toUser(userId, RealtimePublisher.NOTIFICATIONS,
                new NotificationPush(view, repository.countByUserIdAndReadAtIsNull(userId)));
        return view;
    }

    @Transactional(readOnly = true)
    public NotificationPage list(Long userId, Pageable pageable) {
        Page<NotificationView> page = repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                .map(NotificationView::of);
        return new NotificationPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    public NotificationView markRead(Long userId, Long id) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead(clock.instant());
        return NotificationView.of(n);
    }

    public void markAllRead(Long userId) {
        repository.markAllRead(userId, clock.instant());
    }

    public record NotificationView(Long id, NotificationType type, Map<String, Object> params, String link, boolean read,
                                   Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getParams(), n.getLink(), n.getReadAt() != null,
                    n.getCreatedAt());
        }
    }

    /** Tin realtime: thông báo mới + số chưa đọc. */
    public record NotificationPush(NotificationView notification, long unreadCount) {
    }

    public record NotificationPage(List<NotificationView> items, int page, int size, long totalItems,
                                   int totalPages, long unreadCount) {
    }
}
