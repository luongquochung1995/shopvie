package com.shopvi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * P7-3: rate limit đăng nhập theo IP trên Redis (context riêng bật giới hạn với hạn mức nhỏ; các IT khác tắt vì đăng
 * nhập rất nhiều từ cùng localhost).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules.login.capacity=3",
        "app.rate-limit.rules.login.period=1h"})
class RateLimitIT {

    @LocalServerPort
    private int port;

    @Test
    void loginIsThrottledPerIpWithRetryAfter() {
        ApiClient api = new ApiClient(port);
        // Bucket nằm trên Redis (có thể còn từ lần chạy trước): gọi quá hạn mức rồi kiểm lần cuối bị chặn
        ResponseEntity<JsonNode> last = null;
        for (int i = 0; i < 5; i++) {
            last = api.post("/auth/login", null, Map.of("email", "nobody@example.com", "password", "wrong-password"));
        }
        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getBody().at("/error/code").asText()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(Long.parseLong(last.getHeaders().getFirst("Retry-After"))).isPositive();
        // Endpoint không gắn giới hạn vẫn bình thường
        assertThat(api.get("/categories", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
