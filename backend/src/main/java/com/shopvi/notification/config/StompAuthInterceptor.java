package com.shopvi.notification.config;

import com.shopvi.user.UserApi;
import com.shopvi.user.UserStatus;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Xác thực WebSocket: khung STOMP CONNECT phải có header {@code Authorization: Bearer <access token>} (cùng JWT với
 * REST); principal là id user nên {@code convertAndSendToUser(userId, ...)} tới đúng người. Chỉ cho đăng ký hàng đợi
 * riêng {@code /user/queue/**}; client không gửi tin qua socket.
 */
@Component
@RequiredArgsConstructor
class StompAuthInterceptor implements ChannelInterceptor {

    static final String USER_QUEUE_PREFIX = "/user/queue/";

    private final JwtDecoder jwtDecoder;
    private final UserApi userApi;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !destination.startsWith(USER_QUEUE_PREFIX)) {
                throw new MessagingException("Subscription not allowed: " + destination);
            }
        } else if (command == StompCommand.SEND) {
            throw new MessagingException("Sending over WebSocket is not supported; use the REST API");
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring("Bearer ".length()).trim());
        } catch (JwtException ex) {
            throw new MessagingException("Invalid token");
        }
        String userId = jwt.getSubject();
        boolean active = userApi.findById(Long.valueOf(userId)).filter(u -> u.status() == UserStatus.ACTIVE).isPresent();
        if (!active) {
            throw new MessagingException("Account is not active");
        }
        return () -> userId;
    }
}
