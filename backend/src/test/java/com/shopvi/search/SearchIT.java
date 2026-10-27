package com.shopvi.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P5-2: tìm kiếm Elasticsearch — gõ không dấu vẫn ra kết quả có dấu, gợi ý theo tiền tố, chỉ mục đồng bộ với catalog qua
 * Kafka (ẩn sản phẩm → biến khỏi kết quả, hiện lại → có lại).
 */
@IntegrationTest
class SearchIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void searchIgnoresDiacriticsSuggestsAndFollowsCatalogChanges() {
        ApiClient api = new ApiClient(port);
        String admin = api.login("admin@shopvi.local");
        assertThat(api.post("/admin/search/reindex", admin, null).getBody().at("/data/indexed").asInt()).isPositive();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.id, p.name, p.slug, u.email FROM products p JOIN shops s ON s.id = p.shop_id
                JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND p.name ~ '[À-ỹđĐ]'
                ORDER BY p.id LIMIT 1""");
        long productId = ((Number) row.get("id")).longValue();
        String name = (String) row.get("name");
        String query = fold(name);
        assertThat(query).isNotEqualTo(name);

        JsonNode result = api.getRaw("/search?q=" + enc(query) + "&size=60").getBody().at("/data");
        assertThat(result.at("/items").findValuesAsText("id")).contains(String.valueOf(productId));
        assertThat(result.at("/items/0/highlightedName").asText()).contains("<mark>");

        String prefix = name.split("\\s+")[0];
        assertThat(api.getRaw("/search/suggest?q=" + enc(fold(prefix))).getBody().at("/data")).isNotEmpty();

        // Ẩn → ProductChanged → Kafka → indexer xóa khỏi chỉ mục
        String seller = api.login((String) row.get("email"));
        api.exchange(HttpMethod.PATCH, "/seller/products/" + productId + "/visibility", seller,
                Map.of("visible", false), Map.of());
        try {
            await(() -> !contains(api, query, productId), 30);
        } finally {
            api.exchange(HttpMethod.PATCH, "/seller/products/" + productId + "/visibility", seller,
                    Map.of("visible", true), Map.of());
        }
        await(() -> contains(api, query, productId), 30);
    }

    private static boolean contains(ApiClient api, String query, long productId) {
        return api.getRaw("/search?q=" + enc(query) + "&size=60").getBody().at("/data/items")
                .findValuesAsText("id").contains(String.valueOf(productId));
    }

    /** Bỏ dấu tiếng Việt như người dùng gõ không dấu. */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void await(Supplier<Boolean> condition, int seconds) {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        while (!condition.get()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + seconds + "s");
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        }
    }
}
