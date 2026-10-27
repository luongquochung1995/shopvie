package com.shopvi.review.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dữ liệu vào/ra của đánh giá sản phẩm. */
public final class ReviewViews {

    private ReviewViews() {
    }

    public record CreateReviewCommand(int rating, String content, List<String> media) {
    }

    /** @param reviewerName tên người đánh giá đã che bớt, vd. "N*****A" */
    public record ReviewView(Long id, Long orderItemId, String reviewerName, int rating, String content,
                             List<String> media, String variantName, String sellerReply, Instant sellerRepliedAt,
                             Instant createdAt) {
    }

    /**
     * @param counts    số đánh giá theo số sao (1..5)
     * @param withMedia số đánh giá có ảnh
     */
    public record ReviewSummary(BigDecimal ratingAvg, long ratingCount, Map<Integer, Long> counts, long withMedia) {
    }

    public record ProductReviews(ReviewSummary summary, List<ReviewView> items, int page, int size, long totalItems,
                                 int totalPages) {
    }

    /** Đánh giá trong Seller Center (kèm tên sản phẩm). */
    public record SellerReviewView(Long id, Long productId, String productName, String variantName,
                                   String reviewerName, int rating, String content, List<String> media,
                                   String sellerReply, Instant sellerRepliedAt, Instant createdAt) {
    }
}
