package com.shopvi.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.shopvi.support.ApiClient;
import com.shopvi.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class CatalogIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    void categoryTreeIsPublicAndNested() {
        var response = api.get("/categories", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode roots = response.getBody().at("/data");
        assertThat(roots.size()).isGreaterThanOrEqualTo(5);
        assertThat(roots.get(0).at("/children").size()).isGreaterThan(0);
        assertThat(roots.get(0).at("/children/0/parentId").asLong()).isEqualTo(roots.get(0).at("/id").asLong());
    }

    @Test
    void listFiltersByRootCategoryIncludingChildren() {
        long rootId = jdbc.queryForObject("SELECT id FROM categories WHERE slug = 'thoi-trang-nam'", Long.class);
        long expected = jdbc.queryForObject("""
                SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id
                WHERE c.parent_id = ? AND p.status = 'ACTIVE' AND p.deleted_at IS NULL""", Long.class, rootId);

        var response = api.get("/products?categoryId=" + rootId + "&size=5", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().at("/data/totalItems").asLong()).isEqualTo(expected).isPositive();
        assertThat(response.getBody().at("/data/items")).hasSize(5);
        assertThat(response.getBody().at("/data/items/0/thumbnailUrl").asText()).startsWith("https://");
    }

    @Test
    void listSortsByPriceWithinRange() {
        var response = api.get("/products?minPrice=100000&maxPrice=500000&sort=minPrice,asc&size=50", null);

        List<Long> prices = new ArrayList<>();
        response.getBody().at("/data/items").forEach(p -> prices.add(p.at("/minPrice").asLong()));
        assertThat(prices).isNotEmpty().isSorted().allMatch(p -> p >= 100_000 && p <= 500_000);
    }

    @Test
    void listSortsByBestSelling() {
        var response = api.get("/products?sort=soldCount,desc&size=20", null);

        List<Integer> sold = new ArrayList<>();
        response.getBody().at("/data/items").forEach(p -> sold.add(p.at("/soldCount").asInt()));
        assertThat(sold).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    @Test
    void listHidesNonActiveProducts() {
        String slug = jdbc.queryForObject("SELECT slug FROM products ORDER BY id DESC LIMIT 1", String.class);
        jdbc.update("UPDATE products SET status = 'HIDDEN' WHERE slug = ?", slug);
        try {
            assertThat(api.get("/products/" + slug, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode items = api.get("/products?size=100&sort=createdAt,asc", null).getBody().at("/data/items");
            assertThat(items.findValuesAsText("slug")).doesNotContain(slug);
        } finally {
            jdbc.update("UPDATE products SET status = 'ACTIVE' WHERE slug = ?", slug);
        }
    }

    @Test
    void listRejectsUnknownSortAndInvertedPriceRange() {
        assertThat(api.get("/products?sort=passwordHash,asc", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var inverted = api.get("/products?minPrice=10&maxPrice=1", null);
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inverted.getBody().at("/error/details/0/field").asText()).isEqualTo("minPrice");
    }

    @Test
    void productDetailHasVariantsImagesShopAndBreadcrumb() {
        String slug = jdbc.queryForObject("SELECT slug FROM products ORDER BY id LIMIT 1", String.class);

        var response = api.get("/products/" + slug, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode d = response.getBody().at("/data");
        assertThat(d.at("/variants").size()).isPositive();
        assertThat(d.at("/images").size()).isEqualTo(4);
        assertThat(d.at("/breadcrumb").size()).isEqualTo(2);
        assertThat(d.at("/shop/name").asText()).isNotBlank();
        assertThat(d.at("/variantOptions/0/name").asText()).isNotBlank();
        long minVariantPrice = Long.MAX_VALUE;
        for (JsonNode v : d.at("/variants")) {
            minVariantPrice = Math.min(minVariantPrice, v.at("/price").asLong());
        }
        assertThat(d.at("/minPrice").asLong()).isEqualTo(minVariantPrice);
    }

    @Test
    void unknownProductIs404() {
        var response = api.get("/products/does-not-exist", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().at("/error/code").asText()).isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void adminManagesCategoriesAndTreeIsRefreshed() {
        String admin = api.login("admin@shopvi.local");
        String buyer = api.login("buyer@shopvi.local");
        assertThat(api.post("/admin/categories", buyer, Map.of("name", "X")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api.post("/admin/categories", null, Map.of("name", "X")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        var root = api.post("/admin/categories", admin, Map.of("name", "Đồ Chơi IT", "position", 99));
        assertThat(root.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long rootId = root.getBody().at("/data/id").asLong();
        assertThat(root.getBody().at("/data/slug").asText()).isEqualTo("do-choi-it");

        long childId = api.post("/admin/categories", admin, Map.of("name", "Lego IT", "parentId", rootId))
                .getBody().at("/data/id").asLong();
        long grandchildId = api.post("/admin/categories", admin, Map.of("name", "Lego City IT", "parentId", childId))
                .getBody().at("/data/id").asLong();
        var tooDeep = api.post("/admin/categories", admin, Map.of("name", "Quá sâu", "parentId", grandchildId));
        assertThat(tooDeep.getBody().at("/error/code").asText()).isEqualTo("CATEGORY_TOO_DEEP");
        assertThat(api.post("/admin/categories", admin, Map.of("name", "Do choi IT")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // Cache cây danh mục được làm mới sau khi ghi
        assertThat(api.get("/categories", null).getBody().at("/data").findValuesAsText("slug"))
                .contains("do-choi-it", "lego-it", "lego-city-it");
        assertThat(api.delete("/admin/categories/" + rootId, admin).getBody().at("/error/code").asText())
                .isEqualTo("CATEGORY_NOT_EMPTY");

        assertThat(api.put("/admin/categories/" + childId, admin, Map.of("name", "Lego Đổi Tên IT", "position", 1))
                .getBody().at("/data/slug").asText()).isEqualTo("lego-doi-ten-it");

        for (long id : new long[]{grandchildId, childId, rootId}) {
            assertThat(api.delete("/admin/categories/" + id, admin).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        assertThat(api.get("/categories", null).getBody().at("/data").findValuesAsText("slug"))
                .doesNotContain("do-choi-it");
    }
}
