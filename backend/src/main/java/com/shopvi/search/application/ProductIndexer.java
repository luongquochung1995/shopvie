package com.shopvi.search.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.SearchDocument;
import com.shopvi.search.config.SearchProperties;
import com.shopvi.search.infra.ProductDoc;
import com.shopvi.search.infra.ProductIndex;
import java.io.IOException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Đồng bộ sản phẩm vào chỉ mục: cập nhật lẻ theo sự kiện, hoặc nạp lại toàn bộ (P5-2). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexer {

    static final int BATCH = 500;
    private static final DateTimeFormatter SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    private final CatalogApi catalogApi;
    private final ProductIndex index;
    private final SearchProperties properties;
    private final Clock clock;
    private final AtomicBoolean reindexing = new AtomicBoolean(false);

    /** Ghi lại tài liệu của các sản phẩm; sản phẩm không còn bán (ẩn/xóa/shop ngừng) bị gỡ khỏi chỉ mục. */
    public void refresh(Collection<Long> productIds) throws IOException {
        if (!index.aliasExists()) {
            // Chỉ mục chưa được tạo (đang reindex lần đầu): bản nạp toàn bộ sẽ gồm thay đổi này
            return;
        }
        List<SearchDocument> docs = catalogApi.findSearchDocuments(productIds);
        index.bulkIndex(null, docs.stream().map(ProductDoc::of).toList());
        Set<Long> removed = new HashSet<>(productIds);
        docs.forEach(d -> removed.remove(d.id()));
        index.delete(removed);
    }

    /**
     * Nạp lại toàn bộ vào chỉ mục mới rồi chuyển alias (tìm kiếm không gián đoạn trong lúc nạp).
     *
     * @return số sản phẩm đã nạp; -1 nếu đang có một lần reindex khác chạy
     */
    public int reindexAll() throws IOException {
        if (!reindexing.compareAndSet(false, true)) {
            return -1;
        }
        try {
            String newIndex = properties.alias() + "-" + SUFFIX.format(clock.instant());
            index.createIndex(newIndex);
            int total = 0;
            long after = 0;
            List<Long> ids;
            while (!(ids = catalogApi.activeProductIdsAfter(after, BATCH)).isEmpty()) {
                index.bulkIndex(newIndex, catalogApi.findSearchDocuments(ids).stream().map(ProductDoc::of).toList());
                total += ids.size();
                after = ids.get(ids.size() - 1);
            }
            index.refresh(newIndex);
            index.switchAlias(newIndex);
            log.info("Reindexed {} products into {}", total, newIndex);
            return total;
        } finally {
            reindexing.set(false);
        }
    }
}
