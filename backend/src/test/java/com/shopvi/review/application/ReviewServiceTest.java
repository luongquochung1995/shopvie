package com.shopvi.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    @Test
    void reviewerNameKeepsOnlyFirstAndLastCharacter() {
        assertThat(ReviewService.maskName("Nguyễn Văn An")).isEqualTo("N*****n");
        assertThat(ReviewService.maskName("  Đạt ")).isEqualTo("Đ*****t");
        assertThat(ReviewService.maskName("Lê")).isEqualTo("L*****");
        assertThat(ReviewService.maskName(null)).isEqualTo("*****");
        assertThat(ReviewService.maskName(" ")).isEqualTo("*****");
    }
}
