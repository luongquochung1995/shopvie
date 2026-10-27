package com.shopvi.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Áo Thun Nam Đẹp|ao-thun-nam-dep",
            "Điện Thoại & Phụ Kiện|dien-thoai-phu-kien",
            "  Nước hoa  100ml!! |nuoc-hoa-100ml",
            "Levi's 501|levi-s-501",
            "Chăn Ga Gối Ưu Đãi|chan-ga-goi-uu-dai",
            "---|''"
    })
    void slugify(String input, String expected) {
        assertThat(SlugUtils.slugify(input)).isEqualTo(expected);
    }
}
