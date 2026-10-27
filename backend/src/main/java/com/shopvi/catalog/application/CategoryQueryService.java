package com.shopvi.catalog.application;

import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.catalog.domain.Category;
import com.shopvi.catalog.infra.CategoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryQueryService {

    public static final String CACHE = "categories";

    private final CategoryRepository categoryRepository;

    @Cacheable(cacheNames = CACHE, key = "'tree'")
    public CategoryTree getTree() {
        return buildTree(categoryRepository.findAllByOrderByLevelAscPositionAscIdAsc());
    }

    static CategoryTree buildTree(List<Category> categories) {
        Map<Long, List<Category>> byParent = new HashMap<>();
        for (Category c : categories) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        return new CategoryTree(children(null, byParent));
    }

    private static List<CategoryNode> children(Long parentId, Map<Long, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getPosition).thenComparing(Category::getId))
                .map(c -> new CategoryNode(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getLevel(),
                        c.getPosition(), c.getIconUrl(), children(c.getId(), byParent)))
                .toList();
    }
}
