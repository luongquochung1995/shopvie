package com.shopvi.search.application;

import com.shopvi.catalog.ProductChanged;
import com.shopvi.search.config.SearchProperties;
import com.shopvi.search.infra.ProductIndex;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Giữ chỉ mục đồng bộ với catalog: nhận {@link ProductChanged} từ Kafka (catalog → Modulith externalization → Kafka →
 * indexer) và tạo chỉ mục lần đầu khi khởi động. Lỗi khi ghi chỉ mục được ném lại để Kafka consumer thử lại.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.enabled", havingValue = "true", matchIfMissing = true)
class SearchIndexSync {

    static final String GROUP = "shopvi-search-indexer";

    private final ProductIndexer indexer;
    private final ProductIndex index;
    private final SearchProperties properties;

    @KafkaListener(topics = ProductChanged.TOPIC, groupId = GROUP)
    void onProductChanged(ProductChanged event) throws IOException {
        if (event.productId() != null) {
            indexer.refresh(List.of(event.productId()));
        }
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    void createIndexIfMissing() {
        if (!properties.reindexOnStartup()) {
            return;
        }
        try {
            if (!index.aliasExists()) {
                log.info("Search alias '{}' not found — building the product index", properties.alias());
                indexer.reindexAll();
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not build the search index on startup: {}", ex.getMessage());
        }
    }
}
