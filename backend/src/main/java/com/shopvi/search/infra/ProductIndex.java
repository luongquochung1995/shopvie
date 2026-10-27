package com.shopvi.search.infra;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.shopvi.search.application.SearchViews.SearchHit;
import com.shopvi.search.application.SearchViews.SearchQuery;
import com.shopvi.search.application.SearchViews.SearchResult;
import com.shopvi.search.application.SearchViews.Suggestion;
import com.shopvi.search.config.SearchProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Chỉ mục sản phẩm trên Elasticsearch. Đọc/ghi qua alias; reindex tạo chỉ mục mới rồi chuyển alias (không gián đoạn).
 * Từ khóa và tên sản phẩm cùng qua analyzer {@code vi_folded} (lowercase + bỏ dấu) nên "ao thun" khớp "áo thun";
 * trường {@code name.exact} giữ dấu để kết quả gõ đúng dấu được xếp cao hơn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndex {

    private static final String MAPPING = "/search/products-index.json";

    private final ElasticsearchClient client;
    private final SearchProperties properties;

    public boolean aliasExists() throws IOException {
        return client.indices().existsAlias(a -> a.name(properties.alias())).value();
    }

    /** Tạo chỉ mục mới với settings/mapping chuẩn. */
    public void createIndex(String index) throws IOException {
        try (InputStream mapping = ProductIndex.class.getResourceAsStream(MAPPING)) {
            if (mapping == null) {
                throw new IllegalStateException("Missing " + MAPPING);
            }
            client.indices().create(c -> c.index(index).withJson(mapping));
        }
    }

    /** Ghi (thêm/thay) tài liệu vào {@code index}, hoặc vào alias nếu {@code index} null. */
    public void bulkIndex(String index, List<ProductDoc> docs) throws IOException {
        if (docs.isEmpty()) {
            return;
        }
        String target = index == null ? properties.alias() : index;
        // Ghi qua alias: bắt buộc alias đã tồn tại — không để Elasticsearch tự tạo index thường trùng tên alias (sẽ
        // chặn lần chuyển alias của reindex)
        BulkRequest.Builder bulk = new BulkRequest.Builder().requireAlias(index == null);
        docs.forEach(d -> bulk.operations(op -> op.index(i -> i.index(target).id(String.valueOf(d.id())).document(d))));
        BulkResponse res = client.bulk(bulk.build());
        if (res.errors()) {
            res.items().stream().filter(i -> i.error() != null).limit(3)
                    .forEach(i -> log.warn("Index product {} failed: {}", i.id(), i.error().reason()));
            throw new IllegalStateException("Bulk indexing failed");
        }
    }

    public void delete(Collection<Long> ids) throws IOException {
        if (ids.isEmpty()) {
            return;
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        ids.forEach(id -> bulk.operations(op -> op.delete(d -> d.index(properties.alias()).id(String.valueOf(id)))));
        client.bulk(bulk.build());
    }

    public void refresh(String index) throws IOException {
        client.indices().refresh(r -> r.index(index == null ? properties.alias() : index));
    }

    /** Chuyển alias sang {@code newIndex} (nguyên tử) rồi xóa các chỉ mục cũ. */
    public void switchAlias(String newIndex) throws IOException {
        String alias = properties.alias();
        if (!aliasExists() && client.indices().exists(e -> e.index(alias)).value()) {
            // Index thường trùng tên alias (do ghi vào alias khi alias chưa có — phiên bản cũ): dữ liệu không đầy đủ, xóa
            log.warn("Deleting concrete index '{}' that blocks the search alias", alias);
            client.indices().delete(d -> d.index(alias));
        }
        Set<String> old = aliasExists() ? client.indices().getAlias(a -> a.name(alias)).result().keySet() : Set.of();
        client.indices().updateAliases(u -> {
            old.forEach(index -> u.actions(a -> a.remove(r -> r.index(index).alias(alias))));
            return u.actions(a -> a.add(ad -> ad.index(newIndex).alias(alias)));
        });
        for (String index : old) {
            if (!index.equals(newIndex)) {
                client.indices().delete(d -> d.index(index));
            }
        }
    }

    public SearchResult search(SearchQuery q) throws IOException {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        boolean hasKeyword = StringUtils.hasText(q.keyword());
        if (hasKeyword) {
            String keyword = q.keyword().trim();
            // Mọi từ phải khớp (AND) ở một trong các trường; cho phép sai chính tả nhẹ
            bool.must(m -> m.multiMatch(mm -> mm.query(keyword)
                    .fields("name^3", "categoryNames^1.5", "shopName", "keywords")
                    .type(TextQueryType.BestFields).operator(Operator.And).fuzziness("AUTO").prefixLength(1)));
            // Gõ đúng dấu thì xếp trên (vd. "đỏ" ưu tiên "Đỏ" hơn "Đó")
            bool.should(s -> s.match(m -> m.field("name.exact").query(keyword).boost(2f)));
        }
        if (q.categoryId() != null) {
            bool.filter(f -> f.term(t -> t.field("categoryIds").value(FieldValue.of(q.categoryId()))));
        }
        if (q.minPrice() != null || q.maxPrice() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field("minPrice");
                if (q.minPrice() != null) {
                    n.gte(q.minPrice().doubleValue());
                }
                if (q.maxPrice() != null) {
                    n.lte(q.maxPrice().doubleValue());
                }
                return n;
            })));
        }
        SearchResponse<ProductDoc> res = client.search(s -> {
            s.index(properties.alias()).from(q.page() * q.size()).size(q.size())
                    .query(query -> query.bool(bool.build()))
                    .trackTotalHits(t -> t.enabled(true));
            switch (q.sort()) {
                case BEST_SELLING -> s.sort(o -> o.field(f -> f.field("soldCount").order(SortOrder.Desc)));
                case NEWEST -> s.sort(o -> o.field(f -> f.field("createdAt").order(SortOrder.Desc)));
                case PRICE_ASC -> s.sort(o -> o.field(f -> f.field("minPrice").order(SortOrder.Asc)));
                case PRICE_DESC -> s.sort(o -> o.field(f -> f.field("minPrice").order(SortOrder.Desc)));
                case RELEVANCE -> {
                    s.sort(o -> o.score(sc -> sc.order(SortOrder.Desc)));
                    s.sort(o -> o.field(f -> f.field("soldCount").order(SortOrder.Desc)));
                }
            }
            s.sort(o -> o.field(f -> f.field("id").order(SortOrder.Desc)));
            if (hasKeyword) {
                // encoder html: escape tên sản phẩm trước khi bọc <mark> → an toàn khi frontend render HTML
                s.highlight(h -> h.encoder(HighlighterEncoder.Html).preTags("<mark>").postTags("</mark>")
                        .requireFieldMatch(false).fields("name", f -> f.numberOfFragments(0)));
            }
            return s;
        }, ProductDoc.class);

        List<SearchHit> items = new ArrayList<>();
        for (Hit<ProductDoc> hit : res.hits().hits()) {
            ProductDoc d = hit.source();
            if (d == null) {
                continue;
            }
            List<String> highlighted = hit.highlight().get("name");
            items.add(new SearchHit(d.id(), d.name(), highlighted == null || highlighted.isEmpty() ? null : highlighted.get(0),
                    d.slug(), d.thumbnailUrl(), d.minPrice(), d.maxPrice(), d.soldCount(), d.ratingAvg(),
                    d.ratingCount(), d.shopName()));
        }
        long total = res.hits().total() == null ? items.size() : res.hits().total().value();
        return new SearchResult(items, q.page(), q.size(), total, (int) Math.ceil((double) total / q.size()));
    }

    /** Gợi ý theo tiền tố (search-as-you-type, bỏ dấu), tên trùng chỉ lấy một. */
    public List<Suggestion> suggest(String prefix, int size) throws IOException {
        SearchResponse<ProductDoc> res = client.search(s -> s.index(properties.alias()).size(size * 2)
                .source(src -> src.filter(f -> f.includes("name", "slug")))
                .query(q -> q.multiMatch(mm -> mm.query(prefix.trim()).type(TextQueryType.BoolPrefix)
                        .fields("name.suggest", "name.suggest._2gram", "name.suggest._3gram")))
                .sort(o -> o.score(sc -> sc.order(SortOrder.Desc)))
                .sort(o -> o.field(f -> f.field("soldCount").order(SortOrder.Desc))), ProductDoc.class);
        Map<String, Suggestion> distinct = new LinkedHashMap<>();
        for (Hit<ProductDoc> hit : res.hits().hits()) {
            if (hit.source() != null && distinct.size() < size) {
                distinct.putIfAbsent(hit.source().name().toLowerCase(), new Suggestion(hit.source().name(), hit.source().slug()));
            }
        }
        return List.copyOf(distinct.values());
    }
}
