package com.shopvi.catalog.web;

import com.shopvi.catalog.application.CategoryAdminService;
import com.shopvi.catalog.application.CategoryAdminService.CategoryCommand;
import com.shopvi.catalog.domain.Category;
import com.shopvi.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Categories", description = "Quản trị danh mục (role ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
class CategoryAdminController {

    private final CategoryAdminService categoryAdminService;

    @Operation(summary = "Tạo danh mục; slug tự sinh từ tên nếu bỏ trống; tối đa 3 cấp")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest req) {
        return ApiResponse.ok(CategoryResponse.from(categoryAdminService.create(
                new CategoryCommand(req.parentId(), req.name(), req.slug(), req.position(), req.iconUrl()))));
    }

    @Operation(summary = "Sửa tên, slug, thứ tự, icon của danh mục")
    @PutMapping("/{id}")
    ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest req) {
        return ApiResponse.ok(CategoryResponse.from(categoryAdminService.update(id,
                new CategoryCommand(null, req.name(), req.slug(), req.position(), req.iconUrl()))));
    }

    @Operation(summary = "Xóa danh mục rỗng (không có danh mục con và sản phẩm)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        categoryAdminService.delete(id);
    }

    record CreateCategoryRequest(Long parentId, @NotBlank @Size(max = 100) String name,
                                 @Size(max = 120) String slug, @Min(0) int position,
                                 @Size(max = 500) String iconUrl) {
    }

    record UpdateCategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 120) String slug,
                                 @Min(0) int position, @Size(max = 500) String iconUrl) {
    }

    record CategoryResponse(Long id, Long parentId, String name, String slug, int level, int position,
                            String iconUrl) {
        static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getLevel(),
                    c.getPosition(), c.getIconUrl());
        }
    }
}
