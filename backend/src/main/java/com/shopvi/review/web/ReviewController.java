package com.shopvi.review.web;

import static com.shopvi.common.util.ValidationPatterns.HTTP_URL;
import static com.shopvi.common.util.ValidationPatterns.HTTP_URL_MESSAGE;

import com.shopvi.common.ratelimit.RateLimit;
import com.shopvi.common.security.CurrentUserId;
import com.shopvi.common.web.ApiResponse;
import com.shopvi.common.web.PageResponse;
import com.shopvi.review.application.ReviewService;
import com.shopvi.review.application.ReviewViews.CreateReviewCommand;
import com.shopvi.review.application.ReviewViews.ProductReviews;
import com.shopvi.review.application.ReviewViews.ReviewView;
import com.shopvi.review.application.ReviewViews.SellerReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews", description = "Đánh giá sản phẩm: người mua đánh giá đơn đã hoàn thành, xem công khai, seller phản hồi")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Đánh giá một sản phẩm trong đơn đã hoàn thành (mỗi dòng đơn 1 lần)",
            description = "422 REVIEW_NOT_ALLOWED nếu đơn chưa hoàn thành; 409 REVIEW_ALREADY_EXISTS nếu đã đánh giá.")
    @PostMapping("/orders/{code}/items/{itemId}/review")
    @RateLimit(value = "review", key = RateLimit.Key.USER)
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ReviewView> create(@CurrentUserId Long userId, @PathVariable String code, @PathVariable Long itemId,
                                   @Valid @RequestBody CreateReviewRequest req) {
        return ApiResponse.ok(reviewService.create(userId, code, itemId,
                new CreateReviewCommand(req.rating(), req.content(), req.media())));
    }

    @Operation(summary = "Đánh giá của tôi cho các sản phẩm trong một đơn")
    @GetMapping("/orders/{code}/reviews")
    ApiResponse<List<ReviewView>> ofOrder(@CurrentUserId Long userId, @PathVariable String code) {
        return ApiResponse.ok(reviewService.ofOrder(userId, code));
    }

    @Operation(summary = "Đánh giá của sản phẩm (public), mới nhất trước; kèm thống kê theo số sao")
    @GetMapping("/products/{slug}/reviews")
    ApiResponse<ProductReviews> ofProduct(
            @PathVariable String slug,
            @Parameter(description = "Lọc theo số sao (1-5)") @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @Parameter(description = "Chỉ đánh giá có ảnh") @RequestParam(defaultValue = "false") boolean withMedia,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.ok(reviewService.ofProduct(slug, rating, withMedia, newestFirst(pageable)));
    }

    @Operation(summary = "Đánh giá các sản phẩm của shop (role SELLER)")
    @GetMapping("/seller/reviews")
    ApiResponse<PageResponse<SellerReviewView>> ofShop(
            @CurrentUserId Long sellerId,
            @Parameter(description = "Chỉ đánh giá chưa phản hồi") @RequestParam(defaultValue = "false") boolean unreplied,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(reviewService.ofShop(sellerId, unreplied, newestFirst(pageable))));
    }

    @Operation(summary = "Seller phản hồi đánh giá (1 lần)")
    @PostMapping("/seller/reviews/{id}/reply")
    ApiResponse<SellerReviewView> reply(@CurrentUserId Long sellerId, @PathVariable Long id,
                                        @Valid @RequestBody ReplyRequest req) {
        return ApiResponse.ok(reviewService.reply(sellerId, id, req.content()));
    }

    private static Pageable newestFirst(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    record CreateReviewRequest(
            @Min(1) @Max(5) int rating,
            @Size(max = 1000) String content,
            @Size(max = 5) List<@NotBlank @Size(max = 500) @Pattern(regexp = HTTP_URL, message = HTTP_URL_MESSAGE) String> media) {
    }

    record ReplyRequest(@NotBlank @Size(max = 1000) String content) {
    }
}
