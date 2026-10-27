package com.shopvi.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.common.domain.BaseEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversationTest {

    @Test
    void messageUpdatesPreviewAndUnreadOfTheOtherSide() {
        Conversation c = Conversation.start(1L, 10L);
        c.onMessage(message(101L, ChatSide.BUYER, "Shop ơi còn hàng không?"));
        c.onMessage(message(102L, ChatSide.BUYER, "x".repeat(300)));

        assertThat(c.unreadOf(ChatSide.SHOP)).isEqualTo(2);
        assertThat(c.unreadOf(ChatSide.BUYER)).isZero();
        assertThat(c.getLastMessagePreview()).hasSize(Conversation.PREVIEW_LENGTH);
        // Người gửi coi như đã đọc tới tin của mình; shop chưa xem tin nào
        assertThat(c.lastReadByOtherSide(ChatSide.SHOP)).isEqualTo(102L);
        assertThat(c.lastReadByOtherSide(ChatSide.BUYER)).isNull();

        c.onMessage(message(103L, ChatSide.SHOP, "Còn ạ"));
        assertThat(c.unreadOf(ChatSide.SHOP)).isZero();
        assertThat(c.unreadOf(ChatSide.BUYER)).isEqualTo(1);
        assertThat(c.lastReadByOtherSide(ChatSide.BUYER)).isEqualTo(103L);

        c.markRead(ChatSide.BUYER, 103L);
        c.markRead(ChatSide.BUYER, 101L); // đọc lại tin cũ không lùi mốc đã xem
        assertThat(c.unreadOf(ChatSide.BUYER)).isZero();
        assertThat(c.lastReadByOtherSide(ChatSide.SHOP)).isEqualTo(103L);
    }

    private static Message message(Long id, ChatSide side, String content) {
        Message m = Message.of(1L, side == ChatSide.BUYER ? 1L : 2L, side, content);
        set(m, "id", id);
        set(m, "createdAt", Instant.now());
        return m;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field f = BaseEntity.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
