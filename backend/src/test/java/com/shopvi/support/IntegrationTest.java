package com.shopvi.support;

import com.shopvi.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration test chạy toàn bộ ứng dụng trên cổng ngẫu nhiên với dữ liệu seed dev.
 * <ul>
 *   <li>Mặc định: PostgreSQL/Redis/MinIO bằng Testcontainers (cần Docker; không có Docker thì test bị bỏ qua).</li>
 *   <li>{@code -Dit.external=true}: dùng DB ngoài cấu hình qua {@code spring.datasource.*} (vd. Postgres cài local).</li>
 * </ul>
 * Mọi test dùng chung một Spring context (cùng cấu hình) nên DB chỉ khởi tạo và seed một lần.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerOrExternalCondition.class)
// Bật exporter metrics (mặc định @SpringBootTest tắt) để kiểm /actuator/prometheus
@AutoConfigureObservability(tracing = false)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.auth.jwt-secret=c2hvcHZpLWludGVncmF0aW9uLXRlc3Qtc2VjcmV0LWtleS0xMjM0NTY3ODkw",
        "app.dev-seed.enabled=true",
        "app.media.access-key=minio",
        "app.media.secret-key=minio-secret-123",
        // Thanh toán: cổng giả lập + key test (luồng chữ ký giống thật); job hủy đơn quá hạn được gọi trực tiếp trong test
        "app.payment.sandbox-gateway=true",
        "app.payment.public-base-url=http://localhost",
        "app.payment.frontend-base-url=http://localhost:4200",
        "app.payment.vnpay.tmn-code=ITTMN",
        "app.payment.vnpay.hash-secret=it-vnpay-secret",
        "app.payment.momo.partner-code=ITMOMO",
        "app.payment.momo.access-key=it-momo-access",
        "app.payment.momo.secret-key=it-momo-secret",
        "app.order.expiry-job.enabled=false",
        "app.shipping.ghn.webhook-token=it-ghn-token",
        // Chỉ mục tìm kiếm riêng cho test (không đụng chỉ mục dev khi dùng chung Elasticsearch local)
        "app.search.alias=it-products",
        // Khóa cache riêng (dev và IT dùng chung Redis local)
        "app.cache.key-prefix=shopvi-it:",
        // Rate limit kiểm riêng ở RateLimitIT (IT đăng nhập/đăng ký rất nhiều từ cùng một IP)
        "app.rate-limit.enabled=false"})
public @interface IntegrationTest {
}
