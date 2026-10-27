package com.shopvi.search.web;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.search.application.ProductIndexer;
import com.shopvi.search.application.SearchService;
import com.shopvi.search.application.SearchViews.SearchQuery;
import com.shopvi.search.application.SearchViews.SearchResult;
import com.shopvi.search.application.SearchViews.SortBy;
import com.shopvi.search.application.SearchViews.Suggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Search", description = "Tìm kiếm sản phẩm (Elasticsearch, gõ có dấu hoặc không dấu)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class SearchController {

    private final SearchService searchService;
    private final ProductIndexer indexer;

    @Operation(summary = "Tìm sản phẩm: \"ao thun\" khớp \"áo thun\"; lọc danh mục (gồm danh mục con), khoảng giá",
            description = "sort: RELEVANCE (mặc định) | BEST_SELLING | NEWEST | PRICE_ASC | PRICE_DESC. "
                    + "highlightedName: phần khớp bọc trong <mark>, đã escape HTML.")
    @GetMapping("/search")
    ApiResponse<SearchResult> search(
            @Parameter(description = "Từ khóa") @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @PositiveOrZero Long minPrice,
            @RequestParam(required = false) @PositiveOrZero Long maxPrice,
            @RequestParam(defaultValue = "RELEVANCE") SortBy sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(60) int size) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                    List.of(new ErrorDetail("minPrice", "must be less than or equal to maxPrice")));
        }
        return ApiResponse.ok(searchService.search(new SearchQuery(q, categoryId, minPrice, maxPrice, sort, page, size)));
    }

    @Operation(summary = "Gợi ý tên sản phẩm khi đang gõ (từ 2 ký tự, tối đa 8 gợi ý)")
    @GetMapping("/search/suggest")
    ApiResponse<List<Suggestion>> suggest(@RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return ApiResponse.ok(searchService.suggest(q));
    }

    @Operation(summary = "Nạp lại toàn bộ chỉ mục tìm kiếm (role ADMIN); chạy đồng bộ, trả số sản phẩm đã nạp",
            description = "Tạo chỉ mục mới rồi chuyển alias — tìm kiếm không gián đoạn. indexed = -1 nếu đang có lần nạp khác.")
    @PostMapping("/admin/search/reindex")
    ApiResponse<Map<String, Integer>> reindex() {
        try {
            return ApiResponse.ok(Map.of("indexed", indexer.reindexAll()));
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(ErrorCode.SEARCH_UNAVAILABLE);
        }
    }
}
