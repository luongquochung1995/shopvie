package com.shopvi.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.catalog.application.SellerProductViews.VariantCommand;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariantMatrixTest {

    private static final List<VariantOption> OPTIONS = List.of(
            new VariantOption("Màu", List.of("Đỏ", "Xanh")),
            new VariantOption("Size", List.of("M", "L")));

    private static VariantCommand variant(Map<String, String> values) {
        return new VariantCommand(null, values, 100_000, 5, null, null);
    }

    private static List<VariantCommand> fullMatrix() {
        List<VariantCommand> list = new ArrayList<>();
        for (String color : List.of("Đỏ", "Xanh")) {
            for (String size : List.of("M", "L")) {
                list.add(variant(Map.of("Size", size, "Màu", color)));
            }
        }
        return list;
    }

    @Test
    void normalizeOptionsTrimsNamesAndValues() {
        var options = VariantMatrix.normalizeOptions(List.of(new VariantOption(" Màu ", List.of(" Đỏ", "Xanh "))));

        assertThat(options).containsExactly(new VariantOption("Màu", List.of("Đỏ", "Xanh")));
        assertThat(VariantMatrix.normalizeOptions(null)).isEmpty();
    }

    @Test
    void rejectsDuplicateGroupNamesOrValuesAndTooManyGroups() {
        assertInvalid(() -> VariantMatrix.normalizeOptions(List.of(
                new VariantOption("Màu", List.of("Đỏ")), new VariantOption("màu", List.of("Xanh")))),
                "variantOptions[1].name");
        assertInvalid(() -> VariantMatrix.normalizeOptions(List.of(new VariantOption("Màu", List.of("Đỏ", "đỏ")))),
                "variantOptions[0].values");
        assertInvalid(() -> VariantMatrix.normalizeOptions(List.of(new VariantOption("Màu", List.of()))),
                "variantOptions[0].values");
        assertInvalid(() -> VariantMatrix.normalizeOptions(List.of(new VariantOption("A", List.of("1")),
                new VariantOption("B", List.of("1")), new VariantOption("C", List.of("1")))), "variantOptions");
    }

    @Test
    void acceptsEveryCombinationExactlyOnceAndOrdersValuesByGroup() {
        var values = VariantMatrix.normalizeVariants(OPTIONS, fullMatrix());

        assertThat(values).hasSize(4);
        // Map giữ thứ tự nhóm phân loại (Màu trước Size) dù request gửi theo thứ tự khác
        assertThat(values.get(0).keySet()).containsExactly("Màu", "Size");
        assertThat(values.get(0)).containsEntry("Màu", "Đỏ").containsEntry("Size", "M");
    }

    @Test
    void productWithoutOptionsHasExactlyOneSku() {
        assertThat(VariantMatrix.normalizeVariants(List.of(), List.of(variant(Map.of())))).containsExactly(Map.of());
        assertInvalid(() -> VariantMatrix.normalizeVariants(List.of(), List.of(variant(Map.of()), variant(Map.of()))),
                "variants");
    }

    @Test
    void rejectsMissingDuplicateOrUnknownCombinations() {
        List<VariantCommand> missing = fullMatrix().subList(0, 3);
        assertInvalid(() -> VariantMatrix.normalizeVariants(OPTIONS, missing), "variants");

        List<VariantCommand> duplicate = new ArrayList<>(fullMatrix());
        duplicate.set(3, variant(Map.of("Màu", "Đỏ", "Size", "M")));
        assertInvalid(() -> VariantMatrix.normalizeVariants(OPTIONS, duplicate), "variants[3].optionValues");

        List<VariantCommand> unknownValue = new ArrayList<>(fullMatrix());
        unknownValue.set(0, variant(Map.of("Màu", "Tím", "Size", "M")));
        assertInvalid(() -> VariantMatrix.normalizeVariants(OPTIONS, unknownValue), "variants[0].optionValues");

        List<VariantCommand> extraKey = new ArrayList<>(fullMatrix());
        extraKey.set(0, variant(Map.of("Màu", "Đỏ", "Size", "M", "Chất liệu", "Cotton")));
        assertInvalid(() -> VariantMatrix.normalizeVariants(OPTIONS, extraKey), "variants[0].optionValues");
    }

    @Test
    void rejectsTooManyCombinations() {
        List<String> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            twenty.add("v" + i);
        }
        var big = List.of(new VariantOption("A", twenty), new VariantOption("B", twenty));

        assertInvalid(() -> VariantMatrix.normalizeVariants(big, List.of()), "variantOptions");
    }

    private static void assertInvalid(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(ex.getDetails()).singleElement().extracting("field").isEqualTo(field);
                });
    }
}
