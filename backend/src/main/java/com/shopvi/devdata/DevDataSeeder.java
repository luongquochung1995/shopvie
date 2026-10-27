package com.shopvi.devdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.common.util.SlugUtils;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seed dữ liệu dev (P1-6): 1 admin, 1 buyer, 3 seller + shop, 20 danh mục, 100 sản phẩm có variant.
 * Chỉ chạy khi {@code app.dev-seed.enabled=true} (profile dev) và bảng users đang rỗng.
 * Mọi tài khoản seed dùng mật khẩu {@value #PASSWORD}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
class DevDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "Password@123";
    private static final int PRODUCT_COUNT = 100;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<CacheManager> cacheManager;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        Long users = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        if (users != null && users > 0) {
            log.info("Dev seed skipped: database already has data");
            return;
        }
        tx.executeWithoutResult(status -> seed());
        // Cây danh mục trong Redis có thể còn từ lần chạy trước với DB khác
        cacheManager.ifAvailable(cm -> cm.getCacheNames().forEach(name -> {
            try {
                var cache = cm.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            } catch (RuntimeException ex) {
                log.warn("Could not clear cache {}: {}", name, ex.getMessage());
            }
        }));
        log.info("Dev seed done: {} products. Accounts use password '{}'", PRODUCT_COUNT, PASSWORD);
    }

    private void seed() {
        Random random = new Random(2026);
        String hash = passwordEncoder.encode(PASSWORD);

        insertUser("admin@shopvi.local", "Quản trị viên", "ADMIN", hash);
        insertUser("buyer@shopvi.local", "Người Mua Demo", "BUYER", hash);
        List<Long> shopIds = new ArrayList<>();
        // email, chủ shop, tên shop, địa chỉ lấy hàng (tỉnh, xã, chi tiết) — 3 miền để thử phí ship theo vùng
        String[][] shops = {
                {"seller1@shopvi.local", "Chủ Shop Thời Trang", "ShopVi Fashion", "79", "26740", "12 Lê Lợi"},
                {"seller2@shopvi.local", "Chủ Shop Công Nghệ", "Tech Zone VN", "01", "00004", "25 Kim Mã"},
                {"seller3@shopvi.local", "Chủ Shop Nhà & Đẹp", "Nhà Xinh & Beauty", "48", "20242", "88 Bạch Đằng"},
        };
        for (String[] s : shops) {
            Long ownerId = insertUser(s[0], s[1], "SELLER", hash);
            shopIds.add(jdbc.queryForObject("""
                    INSERT INTO shops (owner_id, name, slug, logo_url, description, rating_avg, status,
                                       pickup_name, pickup_phone, pickup_province_code, pickup_ward_code, pickup_detail)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, '0901234567', ?, ?, ?) RETURNING id""", Long.class,
                    ownerId, s[2], SlugUtils.slugify(s[2]), image("shop-" + SlugUtils.slugify(s[2]), 200),
                    "Shop chính hãng, giao hàng nhanh toàn quốc.", 4.5 + random.nextInt(5) / 10.0,
                    s[1], s[3], s[4], s[5]));
        }

        Map<String, Long> leafIds = insertCategories();
        Instant now = clock.instant();
        List<Template> templates = CatalogTemplates.ALL;
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Template t = templates.get(i % templates.size());
            insertProduct(i + 1, t, shopIds.get(t.shopIndex()), leafIds.get(t.categorySlug()), random,
                    now.minus(Duration.ofHours(7L * i + random.nextInt(6))));
        }
    }

    private Long insertUser(String email, String fullName, String role, String hash) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role, status)
                VALUES (?, ?, ?, ?, 'ACTIVE') RETURNING id""", Long.class, email, hash, fullName, role);
    }

    /** 5 danh mục gốc × 3 danh mục con = 20 danh mục. Trả về slug → id của danh mục con. */
    private Map<String, Long> insertCategories() {
        Map<String, Long> leafIds = new LinkedHashMap<>();
        int rootPos = 0;
        for (var root : CatalogTemplates.CATEGORIES.entrySet()) {
            String rootSlug = SlugUtils.slugify(root.getKey());
            Long rootId = jdbc.queryForObject("""
                    INSERT INTO categories (parent_id, name, slug, level, position, icon_url)
                    VALUES (NULL, ?, ?, 0, ?, ?) RETURNING id""", Long.class,
                    root.getKey(), rootSlug, rootPos++, image("cat-" + rootSlug, 120));
            int childPos = 0;
            for (String child : root.getValue()) {
                String slug = SlugUtils.slugify(child);
                leafIds.put(slug, jdbc.queryForObject("""
                        INSERT INTO categories (parent_id, name, slug, level, position, icon_url)
                        VALUES (?, ?, ?, 1, ?, ?) RETURNING id""", Long.class,
                        rootId, child, slug, childPos++, image("cat-" + slug, 120)));
            }
        }
        return leafIds;
    }

    private void insertProduct(int no, Template t, Long shopId, Long categoryId, Random r, Instant createdAt) {
        String brand = pick(t.brands(), r);
        String name = "%s %s %s".formatted(pick(t.names(), r), brand, pick(CatalogTemplates.SUFFIXES, r)).trim();
        String slug = SlugUtils.slugify(name) + "-" + no;

        // Chọn tập giá trị cho từng nhóm phân loại (giữ thứ tự gốc)
        List<Map<String, Object>> options = new ArrayList<>();
        List<List<String>> chosen = new ArrayList<>();
        for (var group : t.options()) {
            List<String> values = new ArrayList<>(group.values());
            while (values.size() > 2 && r.nextInt(3) == 0) {
                values.remove(r.nextInt(values.size()));
            }
            chosen.add(values);
            options.add(Map.of("name", group.name(), "values", values));
        }

        long basePrice = roundPrice(t.minPrice() + (long) (r.nextDouble() * (t.maxPrice() - t.minPrice())));
        List<Object[]> variants = new ArrayList<>();
        long min = Long.MAX_VALUE;
        long max = 0;
        int index = 0;
        for (List<String> combo : cartesian(chosen)) {
            Map<String, String> optionValues = new LinkedHashMap<>();
            long price = basePrice;
            for (int g = 0; g < combo.size(); g++) {
                optionValues.put(t.options().get(g).name(), combo.get(g));
                // Nhóm phân loại cuối (size/dung lượng...) tăng giá theo bậc
                if (g == combo.size() - 1) {
                    price += t.priceStep() * chosen.get(g).indexOf(combo.get(g));
                }
            }
            price = roundPrice(price);
            min = Math.min(min, price);
            max = Math.max(max, price);
            int stock = r.nextInt(10) == 0 ? 0 : 5 + r.nextInt(146);
            variants.add(new Object[]{"SV%04d-%02d".formatted(no, ++index), json(optionValues), price, stock});
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("Thương hiệu", brand);
        attributes.put("Xuất xứ", pick(CatalogTemplates.ORIGINS, r));
        attributes.putAll(t.attributes());

        int ratingCount = r.nextInt(8) == 0 ? 0 : 1 + r.nextInt(800);
        double rating = ratingCount == 0 ? 0 : 3.5 + r.nextInt(16) / 10.0;
        Long productId = jdbc.queryForObject("""
                INSERT INTO products (shop_id, category_id, name, slug, description, attributes, variant_options,
                                      min_price, max_price, sold_count, rating_avg, rating_count, status,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?) RETURNING id""", Long.class,
                shopId, categoryId, name, slug, description(name, brand), json(attributes), json(options),
                min, max, r.nextInt(5000), rating, ratingCount, Timestamp.from(createdAt), Timestamp.from(createdAt));

        List<Object[]> images = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            images.add(new Object[]{productId, image("p%d-%d".formatted(no, k), 800), k});
        }
        jdbc.batchUpdate("INSERT INTO product_images (product_id, url, position) VALUES (?, ?, ?)", images);
        jdbc.batchUpdate("""
                INSERT INTO product_variants (product_id, sku, option_values, price, stock)
                VALUES (?, ?, ?::jsonb, ?, ?)""",
                variants.stream().map(v -> new Object[]{productId, v[0], v[1], v[2], v[3]}).toList());
    }

    private static String description(String name, String brand) {
        return """
                %s chính hãng %s, hàng mới 100%%.
                - Chất lượng kiểm định, đổi trả trong 15 ngày nếu lỗi do nhà sản xuất.
                - Giao hàng toàn quốc, kiểm tra hàng trước khi thanh toán (COD).
                - Hình ảnh sản phẩm mang tính minh họa.""".formatted(name, brand);
    }

    private static List<List<String>> cartesian(List<List<String>> groups) {
        List<List<String>> result = new ArrayList<>();
        result.add(List.of());
        for (List<String> group : groups) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : result) {
                for (String value : group) {
                    List<String> combo = new ArrayList<>(prefix);
                    combo.add(value);
                    next.add(combo);
                }
            }
            result = next;
        }
        return result;
    }

    /** Làm tròn tới 1.000đ, giá kết thúc bằng 9.000 cho giống thực tế (vd. 189.000đ). */
    private static long roundPrice(long price) {
        long thousands = Math.max(1, price / 1000);
        return (thousands / 10 * 10 + 9) * 1000;
    }

    private static String image(String seed, int size) {
        return "https://picsum.photos/seed/shopvi-%s/%d/%d".formatted(seed, size, size);
    }

    private static <T> T pick(List<T> list, Random r) {
        return list.get(r.nextInt(list.size()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Mẫu sinh sản phẩm cho một danh mục con. */
    record Template(String categorySlug, int shopIndex, List<String> names, List<String> brands,
                    List<OptionGroup> options, long minPrice, long maxPrice, long priceStep,
                    Map<String, String> attributes) {
    }

    record OptionGroup(String name, List<String> values) {
    }
}
