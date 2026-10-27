package com.shopvi.notification.domain;

import com.shopvi.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Hội thoại giữa một người mua và một shop (duy nhất theo cặp). */
@Getter
@Entity
@Table(name = "conversations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

    static final int PREVIEW_LENGTH = 200;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private Long buyerId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private Long shopId;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "buyer_unread", nullable = false)
    private int buyerUnread;

    @Column(name = "shop_unread", nullable = false)
    private int shopUnread;

    @Column(name = "buyer_last_read_message_id")
    private Long buyerLastReadMessageId;

    @Column(name = "shop_last_read_message_id")
    private Long shopLastReadMessageId;

    public static Conversation start(Long buyerId, Long shopId) {
        Conversation c = new Conversation();
        c.buyerId = buyerId;
        c.shopId = shopId;
        return c;
    }

    /** Tin mới: cập nhật xem trước, bên gửi coi như đã đọc tới tin này, bên kia tăng số chưa đọc. */
    public void onMessage(Message m) {
        lastMessagePreview = m.getContent().length() > PREVIEW_LENGTH
                ? m.getContent().substring(0, PREVIEW_LENGTH) : m.getContent();
        lastMessageAt = m.getCreatedAt();
        if (m.getSenderSide() == ChatSide.BUYER) {
            shopUnread++;
            buyerLastReadMessageId = m.getId();
            buyerUnread = 0;
        } else {
            buyerUnread++;
            shopLastReadMessageId = m.getId();
            shopUnread = 0;
        }
    }

    /** Bên {@code side} đã đọc tới tin {@code messageId}. */
    public void markRead(ChatSide side, Long messageId) {
        if (side == ChatSide.BUYER) {
            buyerUnread = 0;
            buyerLastReadMessageId = max(buyerLastReadMessageId, messageId);
        } else {
            shopUnread = 0;
            shopLastReadMessageId = max(shopLastReadMessageId, messageId);
        }
    }

    public int unreadOf(ChatSide side) {
        return side == ChatSide.BUYER ? buyerUnread : shopUnread;
    }

    /** Tin cuối cùng phía bên kia đã đọc (để hiện "Đã xem"). */
    public Long lastReadByOtherSide(ChatSide side) {
        return side == ChatSide.BUYER ? shopLastReadMessageId : buyerLastReadMessageId;
    }

    private static Long max(Long a, Long b) {
        return a == null ? b : b == null ? a : Math.max(a, b);
    }
}
