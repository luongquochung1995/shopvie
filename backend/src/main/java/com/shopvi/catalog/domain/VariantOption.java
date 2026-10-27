package com.shopvi.catalog.domain;

import java.util.List;

/**
 * Một nhóm phân loại của sản phẩm, vd. {@code ("Màu sắc", ["Đỏ", "Xanh"])}.
 */
public record VariantOption(String name, List<String> values) {
}
