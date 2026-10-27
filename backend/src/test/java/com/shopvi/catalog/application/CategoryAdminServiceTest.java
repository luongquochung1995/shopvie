package com.shopvi.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.catalog.application.CategoryAdminService.CategoryCommand;
import com.shopvi.catalog.domain.Category;
import com.shopvi.catalog.infra.CategoryRepository;
import com.shopvi.catalog.infra.ProductRepository;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoryAdminServiceTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryAdminService service = new CategoryAdminService(categories, products);

    @BeforeEach
    void setUp() {
        when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createChildComputesLevelAndSlug() {
        when(categories.findById(1L)).thenReturn(Optional.of(CategoryTreeTest.category(1L, null, 0, "Root", 0)));

        Category created = service.create(new CategoryCommand(1L, " Đồng Hồ Nam ", null, 2, " "));

        assertThat(created.getLevel()).isEqualTo((short) 1);
        assertThat(created.getSlug()).isEqualTo("dong-ho-nam");
        assertThat(created.getName()).isEqualTo("Đồng Hồ Nam");
        assertThat(created.getIconUrl()).isNull();
    }

    @Test
    void rejectsFourthLevel() {
        when(categories.findById(3L)).thenReturn(Optional.of(CategoryTreeTest.category(3L, 2L, 2, "Leaf", 0)));

        assertThatThrownBy(() -> service.create(new CategoryCommand(3L, "Too deep", null, 0, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CATEGORY_TOO_DEEP);
    }

    @Test
    void rejectsDuplicateSlug() {
        when(categories.existsBySlug("ao-thun")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryCommand(null, "Áo Thun", null, 0, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CATEGORY_SLUG_EXISTS);
    }

    @Test
    void cannotDeleteCategoryWithProducts() {
        Category c = CategoryTreeTest.category(4L, null, 0, "Has products", 0);
        when(categories.findById(4L)).thenReturn(Optional.of(c));
        when(products.existsByCategoryId(4L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(4L))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_EMPTY);
        verify(categories, never()).delete(any());
    }
}
