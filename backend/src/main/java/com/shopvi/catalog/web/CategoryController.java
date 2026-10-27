package com.shopvi.catalog.web;

import com.shopvi.catalog.application.CategoryQueryService;
import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Categories", description = "Cây danh mục (public)")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
class CategoryController {

    private final CategoryQueryService categoryQueryService;

    @Operation(summary = "Cây danh mục nhiều cấp, sắp theo position (cache Redis)")
    @GetMapping
    ApiResponse<List<CategoryNode>> tree() {
        return ApiResponse.ok(categoryQueryService.getTree().roots());
    }
}
