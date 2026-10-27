package com.shopvi.notification.web;

import com.shopvi.common.ratelimit.RateLimit;
import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.notification.application.ChatService;
import com.shopvi.notification.application.ChatService.ConversationView;
import com.shopvi.notification.application.ChatService.MessagePage;
import com.shopvi.notification.application.ChatService.MessageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "Chat người mua ↔ shop; tin mới / đã xem đẩy realtime qua STOMP /user/queue/chat")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Mở hội thoại với shop (tạo mới nếu chưa có)")
    @PostMapping("/conversations")
    ApiResponse<ConversationView> open(@CurrentUserId Long userId, @Valid @RequestBody OpenRequest req) {
        return ApiResponse.ok(chatService.open(userId, req.shopId()));
    }

    @Operation(summary = "Hội thoại của tôi (cả phía người mua và phía shop của tôi), mới nhất trước")
    @GetMapping("/conversations")
    ApiResponse<PageResponse<ConversationView>> list(@CurrentUserId Long userId,
                                                     @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(chatService.list(userId, pageable)));
    }

    @Operation(summary = "Tin nhắn của hội thoại (cũ dần); before = id tin cũ nhất đang có để tải thêm")
    @GetMapping("/conversations/{id}/messages")
    ApiResponse<MessagePage> messages(@CurrentUserId Long userId, @PathVariable Long id,
                                      @RequestParam(required = false) Long before,
                                      @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(chatService.messages(userId, id, before, size));
    }

    @Operation(summary = "Gửi tin nhắn")
    @PostMapping("/conversations/{id}/messages")
    @RateLimit(value = "chat", key = RateLimit.Key.USER)
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MessageView> send(@CurrentUserId Long userId, @PathVariable Long id, @Valid @RequestBody SendRequest req) {
        return ApiResponse.ok(chatService.send(userId, id, req.content()));
    }

    @Operation(summary = "Đánh dấu đã đọc tới tin mới nhất")
    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        chatService.markRead(userId, id);
    }

    @Operation(summary = "Tổng số tin chưa đọc (badge chat)")
    @GetMapping("/unread-count")
    ApiResponse<Map<String, Long>> unreadCount(@CurrentUserId Long userId) {
        return ApiResponse.ok(Map.of("count", chatService.unreadCount(userId)));
    }

    record OpenRequest(@NotNull Long shopId) {
    }

    record SendRequest(@NotBlank @Size(max = 2000) String content) {
    }
}
