package com.shopvi.notification.infra;

import com.shopvi.notification.domain.Message;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Tin cũ hơn {@code beforeId} (null = mới nhất), mới nhất trước. */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId AND (:beforeId IS NULL OR m.id < :beforeId)
            ORDER BY m.id DESC""")
    List<Message> findPage(Long conversationId, Long beforeId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);
}
