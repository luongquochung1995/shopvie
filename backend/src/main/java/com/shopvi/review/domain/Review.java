package com.shopvi.review.domain;

import com.shopvi.common.domain.BaseEntity;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Đánh giá của người mua cho một dòng đơn đã hoàn thành (mỗi dòng đơn đúng 1 đánh giá). */
@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Column(name = "order_item_id", nullable = false, unique = true, updatable = false)
    private Long orderItemId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "shop_id", nullable = false, updatable = false)
    private Long shopId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "variant_name", updatable = false)
    private String variantName;

    @Column(nullable = false, updatable = false)
    private short rating;

    @Column(updatable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<String> media;

    @Column(name = "seller_reply")
    private String sellerReply;

    @Column(name = "seller_replied_at")
    private Instant sellerRepliedAt;

    public static Review create(Long orderItemId, Long userId, Long productId, Long shopId, String productName,
                                String variantName, int rating, String content, List<String> media) {
        Review r = new Review();
        r.orderItemId = orderItemId;
        r.userId = userId;
        r.productId = productId;
        r.shopId = shopId;
        r.productName = productName;
        r.variantName = variantName;
        r.rating = (short) rating;
        r.content = content;
        r.media = List.copyOf(media);
        return r;
    }

    /** Seller phản hồi đánh giá (một lần, không sửa). */
    public void reply(String text, Instant now) {
        if (sellerReply != null) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_REPLIED);
        }
        sellerReply = text;
        sellerRepliedAt = now;
    }
}
