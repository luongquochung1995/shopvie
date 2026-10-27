package com.shopvi.catalog.application;

import com.shopvi.catalog.application.SellerProductViews.VariantCommand;
import com.shopvi.catalog.domain.VariantOption;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kiểm tra nhóm phân loại và danh sách SKU của sản phẩm: mỗi tổ hợp giá trị (tích Descartes của các nhóm)
 * có đúng một SKU. Vd. Màu [Đỏ, Xanh] × Size [M, L] → đúng 4 SKU.
 */
public final class VariantMatrix {

    public static final int MAX_OPTION_GROUPS = 2;
    public static final int MAX_VARIANTS = 200;

    private VariantMatrix() {
    }

    /** Chuẩn hóa (trim) và kiểm tra nhóm phân loại: tên nhóm không trùng, giá trị trong nhóm không trùng. */
    public static List<VariantOption> normalizeOptions(List<VariantOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        if (options.size() > MAX_OPTION_GROUPS) {
            throw invalid("variantOptions", "at most " + MAX_OPTION_GROUPS + " option groups are allowed");
        }
        List<VariantOption> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int g = 0; g < options.size(); g++) {
            VariantOption option = options.get(g);
            String name = trim(option.name());
            if (name.isEmpty() || !names.add(name.toLowerCase(Locale.ROOT))) {
                throw invalid("variantOptions[%d].name".formatted(g), "must be non-blank and unique");
            }
            List<String> values = option.values() == null ? List.of()
                    : option.values().stream().map(VariantMatrix::trim).toList();
            Set<String> distinct = values.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            if (values.isEmpty() || values.contains("") || distinct.size() != values.size()) {
                throw invalid("variantOptions[%d].values".formatted(g), "must be non-empty, non-blank and unique");
            }
            result.add(new VariantOption(name, values));
        }
        return result;
    }

    /**
     * Kiểm tra SKU khớp đúng các tổ hợp của {@code options} (đã chuẩn hóa) và trả về {@code optionValues} đã chuẩn hóa
     * của từng SKU theo cùng thứ tự (map giữ thứ tự nhóm).
     */
    public static List<Map<String, String>> normalizeVariants(List<VariantOption> options, List<VariantCommand> variants) {
        int expected = options.stream().mapToInt(o -> o.values().size()).reduce(1, Math::multiplyExact);
        if (expected > MAX_VARIANTS) {
            throw invalid("variantOptions", "at most " + MAX_VARIANTS + " option combinations are allowed");
        }
        if (variants == null || variants.size() != expected) {
            throw invalid("variants", "must contain exactly one SKU per option combination (" + expected + ")");
        }
        List<Map<String, String>> result = new ArrayList<>();
        Set<List<String>> combos = new HashSet<>();
        for (int i = 0; i < variants.size(); i++) {
            Map<String, String> raw = variants.get(i).optionValues() == null ? Map.of() : variants.get(i).optionValues();
            Map<String, String> values = new LinkedHashMap<>();
            raw.forEach((k, v) -> values.put(trim(k), trim(v)));
            Map<String, String> ordered = new LinkedHashMap<>();
            for (VariantOption option : options) {
                String value = values.get(option.name());
                if (value == null || !option.values().contains(value)) {
                    throw invalid("variants[%d].optionValues".formatted(i), "must use the values of every option group");
                }
                ordered.put(option.name(), value);
            }
            if (values.size() != options.size() || !combos.add(List.copyOf(ordered.values()))) {
                throw invalid("variants[%d].optionValues".formatted(i), "must be a unique option combination");
            }
            result.add(ordered);
        }
        return result;
    }

    private static String trim(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                List.of(new ErrorDetail(field, message)));
    }
}
