package com.shopvi.notification.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_side", nullable = false, updatable = false)
    private ChatSide senderSide;

    @Column(nullable = false, updatable = false)
    private String content;

    public static Message of(Long conversationId, Long senderId, ChatSide side, String content) {
        Message m = new Message();
        m.conversationId = conversationId;
        m.senderId = senderId;
        m.senderSide = side;
        m.content = content;
        return m;
    }
}
