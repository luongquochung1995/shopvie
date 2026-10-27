package com.shopvi.notification.web;

import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.notification.application.NotificationService;
import com.shopvi.notification.application.NotificationService.NotificationPage;
import com.shopvi.notification.application.NotificationService.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "Thông báo của tôi; tin mới đẩy realtime qua STOMP /user/queue/notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Thông báo của tôi (mới nhất trước) kèm số chưa đọc")
    @GetMapping
    ApiResponse<NotificationPage> list(@CurrentUserId Long userId,
                                       @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(notificationService.list(userId,
                PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50))));
    }

    @Operation(summary = "Số thông báo chưa đọc (badge chuông)")
    @GetMapping("/unread-count")
    ApiResponse<Map<String, Long>> unreadCount(@CurrentUserId Long userId) {
        return ApiResponse.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    @PostMapping("/{id}/read")
    ApiResponse<NotificationView> markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(userId, id));
    }

    @Operation(summary = "Đánh dấu tất cả đã đọc")
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@CurrentUserId Long userId) {
        notificationService.markAllRead(userId);
    }
}
