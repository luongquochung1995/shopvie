package com.shopvi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Phase 7: correlation id, metrics Prometheus, header bảo mật, chống CSRF cookie refresh, sitemap, cache sản phẩm. */
@IntegrationTest
class PlatformIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void requestIdIsEchoedOrGenerated() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "trace-abc-123");
        ResponseEntity<String> echoed = rest.exchange("/api/v1/categories", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(echoed.getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-abc-123");

        // Giá trị không hợp lệ (dài / ký tự lạ — có thể chèn nội dung vào log) bị thay bằng id mới
        headers.set("X-Request-Id", "bad id <INJECTED> " + "x".repeat(80));
        ResponseEntity<String> replaced = rest.exchange("/api/v1/categories", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(replaced.getHeaders().getFirst("X-Request-Id")).doesNotContain("INJECTED").hasSize(36);
    }

    @Test
    void prometheusExposesHttpLatencyHistogram() {
        rest.getForEntity("/api/v1/categories", String.class);
        ResponseEntity<String> metrics = rest.getForEntity("/actuator/prometheus", String.class);
        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_bucket").contains("application=\"shopvi-backend\"");
        // Các endpoint actuator nhạy cảm không được mở
        assertThat(rest.getForEntity("/actuator/env", String.class).getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void apiSendsSecurityHeaders() {
        HttpHeaders h = rest.getForEntity("/api/v1/categories", String.class).getHeaders();
        assertThat(h.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(h.getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(h.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void refreshFromForeignOriginIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "https://evil.example.com");
        ResponseEntity<String> res = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        headers.set("Origin", "http://localhost:4200");
        // Origin hợp lệ nhưng không có cookie → lỗi xác thực bình thường, không phải 403 Origin
        ResponseEntity<String> allowed = rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sitemapListsCategoriesAndActiveProducts() {
        ApiClient api = new ApiClient(port);
        JsonNode data = api.get("/seo/sitemap", null).getBody().at("/data");
        long active = jdbc.queryForObject("""
                SELECT count(*) FROM products p JOIN shops s ON s.id = p.shop_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND s.status <> 'BANNED'""", Long.class);
        assertThat(data.at("/products")).hasSize((int) active);
        assertThat(data.at("/categories").size()).isPositive();
        assertThat(data.at("/products/0/updatedAt").asText()).isNotBlank();
    }

    @Test
    void cachedProductDetailIsEvictedWhenProductChanges() {
        ApiClient api = new ApiClient(port);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT p.id, p.slug FROM products p JOIN shops s ON s.id = p.shop_id JOIN users u ON u.id = s.owner_id
                WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND u.email = 'seller2@shopvi.local'
                ORDER BY p.id LIMIT 1""");
        long id = ((Number) row.get("id")).longValue();
        String slug = (String) row.get("slug");
        String seller = api.login("seller2@shopvi.local");

        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.OK); // vào cache
        api.exchange(HttpMethod.PATCH, "/seller/products/" + id + "/visibility", seller, Map.of("visible", false), Map.of());
        try {
            assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            api.exchange(HttpMethod.PATCH, "/seller/products/" + id + "/visibility", seller, Map.of("visible", true), Map.of());
        }
        assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
