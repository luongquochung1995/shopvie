package com.shopvi.notification.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.notification.domain.ChatSide;
import com.shopvi.notification.domain.Conversation;
import com.shopvi.notification.domain.Message;
import com.shopvi.notification.infra.ConversationRepository;
import com.shopvi.notification.infra.MessageRepository;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat người mua ↔ shop. Một user có thể ở cả hai phía: người mua (hội thoại với shop khác) và chủ shop (hội thoại của
 * khách với shop mình). Tin mới / đã xem được đẩy realtime tới {@code /user/queue/chat} của cả hai bên.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    static final int MAX_PAGE = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ShopApi shopApi;
    private final UserApi userApi;
    private final RealtimePublisher publisher;
    private final ChatStarter starter;

    /** Mở (hoặc lấy lại) hội thoại với shop đang hoạt động. Không chat với shop của chính mình. */
    public ConversationView open(Long userId, Long shopId) {
        ShopSummary shop = shopApi.findActiveSummary(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_NOT_FOUND));
        if (shopApi.findShopIdOfOwner(userId).filter(shopId::equals).isPresent()) {
            throw new BusinessException(ErrorCode.CHAT_WITH_OWN_SHOP);
        }
        Conversation c = conversationRepository.findByBuyerIdAndShopId(userId, shopId)
                .orElseGet(() -> startOrReload(userId, shopId));
        return new ConversationView(c.getId(), ChatSide.BUYER, shop.id(), shop.name(), shop.logoUrl(),
                c.getLastMessagePreview(), c.getLastMessageAt(), c.unreadOf(ChatSide.BUYER));
    }

    private Conversation startOrReload(Long buyerId, Long shopId) {
        try {
            return starter.start(buyerId, shopId);
        } catch (DataIntegrityViolationException ex) {
            return conversationRepository.findByBuyerIdAndShopId(buyerId, shopId).orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<ConversationView> list(Long userId, Pageable pageable) {
        Long myShopId = shopApi.findShopIdOfOwner(userId).orElse(null);
        Page<Conversation> page = conversationRepository.findMine(userId, myShopId,
                PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE)));
        Map<Long, ShopSummary> shops = shopApi.findSummaries(page.getContent().stream().map(Conversation::getShopId).toList());
        Map<Long, UserAccount> buyers = userApi.findByIds(page.getContent().stream().map(Conversation::getBuyerId).toList());
        return page.map(c -> toView(c, sideOf(c, userId), shops, buyers));
    }

    /** Tin nhắn (cũ dần), trả kèm id tin cuối bên kia đã xem. */
    @Transactional(readOnly = true)
    public MessagePage messages(Long userId, Long conversationId, Long beforeId, int size) {
        Conversation c = participantConversation(userId, conversationId, false);
        ChatSide side = sideOf(c, userId);
        List<Message> newestFirst = messageRepository.findPage(conversationId, beforeId,
                PageRequest.of(0, Math.max(1, Math.min(size, MAX_PAGE))));
        List<MessageView> items = new ArrayList<>(newestFirst.stream().map(MessageView::of).toList());
        Collections.reverse(items);
        return new MessagePage(items, newestFirst.size() == Math.min(size, MAX_PAGE), c.lastReadByOtherSide(side));
    }

    public MessageView send(Long userId, Long conversationId, String content) {
        Conversation c = participantConversation(userId, conversationId, true);
        ChatSide side = sideOf(c, userId);
        Message m = messageRepository.saveAndFlush(Message.of(c.getId(), userId, side, content.trim()));
        c.onMessage(m);
        MessageView view = MessageView.of(m);
        participants(c).forEach((participant, participantSide) -> publisher.toUser(participant, RealtimePublisher.CHAT,
                ChatPush.message(c.getId(), view, c.unreadOf(participantSide))));
        return view;
    }

    /** Đánh dấu đã đọc tới tin mới nhất; báo "Đã xem" cho bên kia. */
    public void markRead(Long userId, Long conversationId) {
        Conversation c = participantConversation(userId, conversationId, true);
        ChatSide side = sideOf(c, userId);
        messageRepository.findFirstByConversationIdOrderByIdDesc(c.getId()).ifPresent(last -> {
            c.markRead(side, last.getId());
            participants(c).forEach((participant, participantSide) -> {
                if (participantSide != side) {
                    publisher.toUser(participant, RealtimePublisher.CHAT, ChatPush.read(c.getId(), last.getId()));
                }
            });
        });
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return conversationRepository.unreadOf(userId, shopApi.findShopIdOfOwner(userId).orElse(null));
    }

    private Conversation participantConversation(Long userId, Long conversationId, boolean lock) {
        Conversation c = (lock ? conversationRepository.findByIdForUpdate(conversationId)
                : conversationRepository.findById(conversationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        boolean participant = c.getBuyerId().equals(userId)
                || shopApi.findShopIdOfOwner(userId).filter(c.getShopId()::equals).isPresent();
        if (!participant) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return c;
    }

    private ChatSide sideOf(Conversation c, Long userId) {
        return c.getBuyerId().equals(userId) ? ChatSide.BUYER : ChatSide.SHOP;
    }

    /** user id → phía của họ (chủ shop có thể không còn — bỏ qua). */
    private Map<Long, ChatSide> participants(Conversation c) {
        Map<Long, ChatSide> result = new HashMap<>();
        result.put(c.getBuyerId(), ChatSide.BUYER);
        shopApi.findOwnerId(c.getShopId()).ifPresent(owner -> result.put(owner, ChatSide.SHOP));
        return result;
    }

    private static ConversationView toView(Conversation c, ChatSide side, Map<Long, ShopSummary> shops,
                                           Map<Long, UserAccount> buyers) {
        String name;
        String avatar = null;
        if (side == ChatSide.BUYER) {
            ShopSummary shop = shops.get(c.getShopId());
            name = shop == null ? null : shop.name();
            avatar = shop == null ? null : shop.logoUrl();
        } else {
            UserAccount buyer = buyers.get(c.getBuyerId());
            name = buyer == null ? null : buyer.fullName();
        }
        return new ConversationView(c.getId(), side, c.getShopId(), name, avatar, c.getLastMessagePreview(),
                c.getLastMessageAt(), c.unreadOf(side));
    }

    /**
     * @param side          phía của tôi trong hội thoại
     * @param counterpart   tên bên kia: tên shop (khi tôi là người mua) hoặc tên khách (khi tôi là shop)
     */
    public record ConversationView(Long id, ChatSide side, Long shopId, String counterpart, String counterpartAvatar,
                                   String lastMessage, Instant lastMessageAt, int unread) {
    }

    public record MessageView(Long id, Long conversationId, Long senderId, ChatSide senderSide, String content,
                              Instant createdAt) {
        static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getConversationId(), m.getSenderId(), m.getSenderSide(), m.getContent(),
                    m.getCreatedAt());
        }
    }

    /** @param otherLastReadMessageId tin cuối bên kia đã xem (null = chưa xem tin nào) */
    public record MessagePage(List<MessageView> items, boolean hasMore, Long otherLastReadMessageId) {
    }

    /**
     * Tin realtime: {@code MESSAGE} (tin mới + số chưa đọc của người nhận trong hội thoại) hoặc {@code READ}
     * (bên kia đã xem tới {@code lastReadMessageId}).
     */
    public record ChatPush(String type, Long conversationId, MessageView message, Integer unread,
                           Long lastReadMessageId) {
        static ChatPush message(Long conversationId, MessageView m, int unread) {
            return new ChatPush("MESSAGE", conversationId, m, unread, null);
        }

        static ChatPush read(Long conversationId, Long lastReadMessageId) {
            return new ChatPush("READ", conversationId, null, null, lastReadMessageId);
        }
    }
}
