package com.shopvi.catalog.application;

import com.shopvi.catalog.domain.Category;
import com.shopvi.catalog.infra.CategoryRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Quản trị danh mục (role ADMIN). Mọi thay đổi xóa cache cây danh mục (sau khi transaction commit).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryAdminService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @CacheEvict(cacheNames = CategoryQueryService.CACHE, allEntries = true)
    public Category create(CategoryCommand cmd) {
        int level = 0;
        if (cmd.parentId() != null) {
            Category parent = categoryRepository.findById(cmd.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Parent category not found"));
            level = parent.getLevel() + 1;
            if (level > Category.MAX_LEVEL) {
                throw new BusinessException(ErrorCode.CATEGORY_TOO_DEEP);
            }
        }
        String slug = resolveSlug(cmd);
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS);
        }
        return categoryRepository.save(Category.create(cmd.parentId(), level, cmd.name().trim(), slug,
                cmd.position(), emptyToNull(cmd.iconUrl())));
    }

    @CacheEvict(cacheNames = CategoryQueryService.CACHE, allEntries = true)
    public Category update(Long id, CategoryCommand cmd) {
        Category category = get(id);
        String slug = resolveSlug(cmd);
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS);
        }
        category.update(cmd.name().trim(), slug, cmd.position(), emptyToNull(cmd.iconUrl()));
        return category;
    }

    @CacheEvict(cacheNames = CategoryQueryService.CACHE, allEntries = true)
    public void delete(Long id) {
        Category category = get(id);
        if (categoryRepository.existsByParentId(id) || productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY);
        }
        categoryRepository.delete(category);
    }

    private Category get(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private static String resolveSlug(CategoryCommand cmd) {
        String slug = SlugUtils.slugify(StringUtils.hasText(cmd.slug()) ? cmd.slug() : cmd.name());
        if (slug.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Slug must contain letters or digits");
        }
        return slug;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** {@code parentId} chỉ dùng khi tạo mới (không hỗ trợ chuyển danh mục sang cha khác). */
    public record CategoryCommand(Long parentId, String name, String slug, int position, String iconUrl) {
    }
}
