package com.shopvi.review.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.OrderApi;
import com.shopvi.order.OrderApi.CompletedItem;
import com.shopvi.review.application.ReviewViews.CreateReviewCommand;
import com.shopvi.review.application.ReviewViews.ProductReviews;
import com.shopvi.review.application.ReviewViews.ReviewSummary;
import com.shopvi.review.application.ReviewViews.ReviewView;
import com.shopvi.review.application.ReviewViews.SellerReviewView;
import com.shopvi.review.domain.Review;
import com.shopvi.review.infra.ReviewRepository;
import com.shopvi.review.infra.ReviewRepository.RatingAggregate;
import com.shopvi.shop.ShopApi;
import com.shopvi.user.UserAccount;
import com.shopvi.user.UserApi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Đánh giá sản phẩm (P5-6): chỉ dòng đơn đã hoàn thành, mỗi dòng 1 lần; cập nhật điểm trung bình của sản phẩm và shop
 * trong cùng transaction; seller phản hồi 1 lần.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    static final int MAX_MEDIA = 5;

    private final ReviewRepository repository;
    private final OrderApi orderApi;
    private final CatalogApi catalogApi;
    private final ShopApi shopApi;
    private final UserApi userApi;
    private final Clock clock;

    @Transactional
    public ReviewView create(Long userId, String orderCode, Long orderItemId, CreateReviewCommand cmd) {
        CompletedItem item = orderApi.findCompletedItem(userId, orderCode, orderItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED));
        if (repository.existsByOrderItemId(orderItemId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        List<String> media = cmd.media() == null ? List.of()
                : cmd.media().stream().map(String::trim).filter(StringUtils::hasText).distinct().limit(MAX_MEDIA).toList();
        Review review = repository.save(Review.create(orderItemId, userId, item.productId(), item.shopId(),
                item.productName(), item.variantName(), cmd.rating(),
                StringUtils.hasText(cmd.content()) ? cmd.content().trim() : null, media));
        repository.flush();
        refreshRatings(item.productId(), item.shopId());
        return toView(review, userApi.findByIds(List.of(userId)));
    }

    /** Đánh giá của người mua cho các dòng của một đơn (để biết dòng nào đã đánh giá). */
    public List<ReviewView> ofOrder(Long userId, String orderCode) {
        List<Long> itemIds = orderApi.itemIdsOfOrder(userId, orderCode);
        if (itemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        Map<Long, UserAccount> users = userApi.findByIds(List.of(userId));
        return repository.findByOrderItemIdIn(itemIds).stream().map(r -> toView(r, users)).toList();
    }

    /** Đánh giá công khai của sản phẩm, lọc theo số sao / có ảnh, kèm thống kê. */
    public ProductReviews ofProduct(String productSlug, Integer rating, boolean withMedia, Pageable pageable) {
        Long productId = catalogApi.findActiveProductId(productSlug)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        Specification<Review> spec = (root, q, cb) -> cb.equal(root.get("productId"), productId);
        if (rating != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("rating"), rating.shortValue()));
        }
        if (withMedia) {
            spec = spec.and((root, q, cb) -> cb.greaterThan(
                    cb.function("jsonb_array_length", Integer.class, root.get("media")), 0));
        }
        Page<Review> page = repository.findAll(spec, pageable);
        Map<Long, UserAccount> users = userApi.findByIds(page.getContent().stream().map(Review::getUserId).toList());
        return new ProductReviews(summary(productId), page.getContent().stream().map(r -> toView(r, users)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public Page<SellerReviewView> ofShop(Long sellerId, boolean unrepliedOnly, Pageable pageable) {
        Long shopId = shopApi.requireShopIdOfOwner(sellerId);
        Page<Review> page = unrepliedOnly ? repository.findByShopIdAndSellerReplyIsNull(shopId, pageable)
                : repository.findByShopId(shopId, pageable);
        Map<Long, UserAccount> users = userApi.findByIds(page.getContent().stream().map(Review::getUserId).toList());
        return page.map(r -> toSellerView(r, users));
    }

    @Transactional
    public SellerReviewView reply(Long sellerId, Long reviewId, String text) {
        Long shopId = shopApi.requireActiveShopIdOfOwner(sellerId);
        Review review = repository.findByIdAndShopId(reviewId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        review.reply(text.trim(), clock.instant());
        repository.flush();
        return toSellerView(review, userApi.findByIds(List.of(review.getUserId())));
    }

    ReviewSummary summary(Long productId) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            counts.put(star, 0L);
        }
        repository.countByRating(productId).forEach(c -> counts.put((int) c.getRating(), c.getCount()));
        RatingAggregate agg = repository.aggregateOfProduct(productId);
        return new ReviewSummary(round(agg.getAverage()), agg.getCount(), counts, repository.countWithMedia(productId));
    }

    private void refreshRatings(Long productId, Long shopId) {
        RatingAggregate product = repository.aggregateOfProduct(productId);
        catalogApi.updateRating(productId, round(product.getAverage()), (int) product.getCount());
        shopApi.updateRating(shopId, round(repository.aggregateOfShop(shopId).getAverage()));
    }

    /** Trung bình làm tròn 1 chữ số thập phân (cột NUMERIC(2,1)); chưa có đánh giá = 0. */
    static BigDecimal round(Double average) {
        return average == null ? BigDecimal.ZERO.setScale(1) : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    /** "Nguyễn Văn An" → "N*****n": không lộ tên đầy đủ người mua. */
    static String maskName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return "*****";
        }
        String name = fullName.trim();
        int[] cps = name.codePoints().toArray();
        if (cps.length <= 2) {
            return new String(cps, 0, 1) + "*****";
        }
        return new String(cps, 0, 1) + "*****" + new String(cps, cps.length - 1, 1);
    }

    private static ReviewView toView(Review r, Map<Long, UserAccount> users) {
        UserAccount user = users.get(r.getUserId());
        return new ReviewView(r.getId(), r.getOrderItemId(), maskName(user == null ? null : user.fullName()),
                r.getRating(), r.getContent(), r.getMedia(), r.getVariantName(), r.getSellerReply(),
                r.getSellerRepliedAt(), r.getCreatedAt());
    }

    private static SellerReviewView toSellerView(Review r, Map<Long, UserAccount> users) {
        UserAccount user = users.get(r.getUserId());
        return new SellerReviewView(r.getId(), r.getProductId(), r.getProductName(), r.getVariantName(),
                maskName(user == null ? null : user.fullName()), r.getRating(), r.getContent(), r.getMedia(),
                r.getSellerReply(), r.getSellerRepliedAt(), r.getCreatedAt());
    }
}
