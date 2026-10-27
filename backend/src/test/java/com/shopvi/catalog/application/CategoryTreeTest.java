package com.shopvi.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.catalog.application.CategoryTree.CategoryNode;
import com.shopvi.catalog.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CategoryTreeTest {

    private final CategoryTree tree = CategoryQueryService.buildTree(List.of(
            category(1L, null, 0, "Thời Trang", 1),
            category(2L, null, 0, "Điện Thoại", 0),
            category(3L, 1L, 1, "Áo", 1),
            category(4L, 1L, 1, "Quần", 0),
            category(5L, 3L, 2, "Áo Thun", 0)));

    @Test
    void buildsNestedTreeOrderedByPosition() {
        assertThat(tree.roots()).extracting(CategoryNode::id).containsExactly(2L, 1L);
        CategoryNode fashion = tree.roots().get(1);
        assertThat(fashion.children()).extracting(CategoryNode::id).containsExactly(4L, 3L);
        assertThat(fashion.children().get(1).children()).extracting(CategoryNode::name).containsExactly("Áo Thun");
    }

    @Test
    void selfAndDescendants() {
        assertThat(tree.selfAndDescendantIds(1L)).containsExactlyInAnyOrder(1L, 3L, 4L, 5L);
        assertThat(tree.selfAndDescendantIds(5L)).containsExactly(5L);
        assertThat(tree.selfAndDescendantIds(99L)).isEmpty();
    }

    @Test
    void breadcrumbFromRoot() {
        assertThat(tree.breadcrumb(5L)).extracting(CategoryNode::id).containsExactly(1L, 3L, 5L);
        assertThat(tree.breadcrumb(99L)).isEmpty();
    }

    static Category category(Long id, Long parentId, int level, String name, int position) {
        Category c = Category.create(parentId, level, name, name.toLowerCase(), position, null);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
