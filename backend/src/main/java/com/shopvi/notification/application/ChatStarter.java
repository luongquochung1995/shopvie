package com.shopvi.notification.application;

import com.shopvi.notification.domain.Conversation;
import com.shopvi.notification.infra.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo hội thoại trong transaction riêng: hai request mở cùng lúc thì một bên dính UNIQUE (buyer, shop) — transaction
 * của nó rollback riêng, người gọi đọc lại bản đã có.
 */
@Component
@RequiredArgsConstructor
class ChatStarter {

    private final ConversationRepository conversationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Conversation start(Long buyerId, Long shopId) {
        return conversationRepository.saveAndFlush(Conversation.start(buyerId, shopId));
    }
}
