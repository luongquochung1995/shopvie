package com.shopvi.catalog.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Cây danh mục (giá trị được cache trong Redis dưới dạng JSON).
 */
public record CategoryTree(List<CategoryNode> roots) {

    public record CategoryNode(Long id, Long parentId, String name, String slug, int level, int position,
                               String iconUrl, List<CategoryNode> children) {
    }

    public Optional<CategoryNode> find(Long id) {
        return stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /** Id của danh mục và toàn bộ danh mục con cháu (để lọc sản phẩm theo danh mục cha). */
    public List<Long> selfAndDescendantIds(Long id) {
        return find(id).map(node -> flatten(List.of(node)).stream().map(CategoryNode::id).toList())
                .orElse(List.of());
    }

    /** Đường dẫn từ gốc tới danh mục, vd. [Thời Trang Nam, Áo thun nam]. */
    public List<CategoryNode> breadcrumb(Long id) {
        List<CategoryNode> path = new ArrayList<>();
        Optional<CategoryNode> current = find(id);
        while (current.isPresent()) {
            path.add(current.get());
            Long parentId = current.get().parentId();
            current = parentId == null ? Optional.empty() : find(parentId);
        }
        Collections.reverse(path);
        return path;
    }

    private java.util.stream.Stream<CategoryNode> stream() {
        return flatten(roots).stream();
    }

    private static List<CategoryNode> flatten(List<CategoryNode> start) {
        List<CategoryNode> result = new ArrayList<>();
        Deque<CategoryNode> stack = new ArrayDeque<>(start);
        while (!stack.isEmpty()) {
            CategoryNode node = stack.pop();
            result.add(node);
            node.children().forEach(stack::push);
        }
        return result;
    }
}
