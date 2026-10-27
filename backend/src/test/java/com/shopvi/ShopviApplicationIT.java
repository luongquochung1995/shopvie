package com.shopvi;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ShopviApplicationIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void readinessIsUp() {
        var response = rest.getForEntity("/actuator/health/readiness", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void flywayCreatesCoreTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("users", "refresh_tokens", "addresses", "shops", "categories",
                "products", "product_images", "product_variants");
    }

    @Test
    void devSeedCreatesCatalog() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories", Long.class)).isGreaterThanOrEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isGreaterThanOrEqualTo(100);
        // 3 shop seed (test khác có thể tạo thêm shop trên cùng DB)
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM shops s JOIN users u ON u.id = s.owner_id
                WHERE s.status = 'ACTIVE' AND u.email LIKE 'seller_@shopvi.local'""", Long.class)).isEqualTo(3);
        // Mỗi sản phẩm có ít nhất 1 variant và min/max price khớp giá variant
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM products p WHERE NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id)
                   OR p.min_price <> (SELECT min(price) FROM product_variants v WHERE v.product_id = p.id)
                   OR p.max_price <> (SELECT max(price) FROM product_variants v WHERE v.product_id = p.id)""",
                Long.class)).isZero();
    }

    @Test
    void openApiDocsArePublic() {
        var response = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("ShopVi API", "/api/v1/auth/login", "/api/v1/products/{slug}");
    }
}
