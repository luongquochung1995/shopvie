package com.shopvi.catalog.web;

import com.shopvi.catalog.application.CategoryQueryService;
import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.shop.ShopApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SEO", description = "Dữ liệu cho sitemap.xml (SSR server ghép URL tuyệt đối)")
@RestController
@RequestMapping("/api/v1/seo")
@RequiredArgsConstructor
class SeoController {

    /** Một file sitemap tối đa 50.000 URL; quá số này cần sitemap index (xem Backlog). */
    static final int MAX_PRODUCTS = 45_000;

    private final CategoryQueryService categoryQueryService;
    private final ProductRepository productRepository;
    private final ShopApi shopApi;

    @Operation(summary = "Slug danh mục và sản phẩm đang bán (của shop không bị khóa), mới cập nhật trước")
    @GetMapping("/sitemap")
    @Transactional(readOnly = true)
    ApiResponse<Sitemap> sitemap() {
        List<CategoryEntry> categories = new ArrayList<>();
        categoryQueryService.getTree().roots().forEach(root -> flatten(root).forEach(n -> categories.add(new CategoryEntry(n.slug()))));
        Set<Long> banned = shopApi.bannedShopIds();
        List<ProductEntry> products = productRepository.findSitemapEntries(PageRequest.of(0, MAX_PRODUCTS)).stream()
                .filter(row -> !banned.contains((Long) row[2]))
                .map(row -> new ProductEntry((String) row[0], (Instant) row[1]))
                .toList();
        return ApiResponse.ok(new Sitemap(categories, products));
    }

    private static Stream<CategoryNode> flatten(CategoryNode node) {
        return Stream.concat(Stream.of(node), node.children().stream().flatMap(SeoController::flatten));
    }

    record Sitemap(List<CategoryEntry> categories, List<ProductEntry> products) {
    }

    record CategoryEntry(String slug) {
    }

    record ProductEntry(String slug, Instant updatedAt) {
    }
}
