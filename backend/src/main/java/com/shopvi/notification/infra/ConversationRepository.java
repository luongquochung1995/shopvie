package com.shopvi.notification.infra;

import com.shopvi.notification.domain.Conversation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByBuyerIdAndShopId(Long buyerId, Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(Long id);

    /** Hội thoại của tôi: là người mua, hoặc là chủ shop {@code shopId} (null nếu không có shop). */
    @Query(value = """
            SELECT c FROM Conversation c
            WHERE c.buyerId = :userId OR c.shopId = :shopId
            ORDER BY c.lastMessageAt DESC NULLS LAST, c.id DESC""",
            countQuery = "SELECT COUNT(c) FROM Conversation c WHERE c.buyerId = :userId OR c.shopId = :shopId")
    Page<Conversation> findMine(Long userId, Long shopId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN c.buyerId = :userId THEN c.buyerUnread ELSE c.shopUnread END), 0)
            FROM Conversation c WHERE c.buyerId = :userId OR c.shopId = :shopId""")
    long unreadOf(Long userId, Long shopId);
}
