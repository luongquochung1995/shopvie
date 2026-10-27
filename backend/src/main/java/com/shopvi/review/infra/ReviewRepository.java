package com.shopvi.review.infra;

import com.shopvi.review.domain.Review;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    boolean existsByOrderItemId(Long orderItemId);

    List<Review> findByOrderItemIdIn(Collection<Long> orderItemIds);

    Optional<Review> findByIdAndShopId(Long id, Long shopId);

    Page<Review> findByShopId(Long shopId, Pageable pageable);

    Page<Review> findByShopIdAndSellerReplyIsNull(Long shopId, Pageable pageable);

    /** Số đánh giá theo từng mức sao của sản phẩm. */
    @Query("SELECT r.rating AS rating, COUNT(r) AS count FROM Review r WHERE r.productId = :productId GROUP BY r.rating")
    List<RatingCount> countByRating(Long productId);

    @Query(value = "SELECT count(*) FROM reviews WHERE product_id = :productId AND jsonb_array_length(media) > 0",
            nativeQuery = true)
    long countWithMedia(Long productId);

    @Query("SELECT AVG(r.rating) AS average, COUNT(r) AS count FROM Review r WHERE r.productId = :productId")
    RatingAggregate aggregateOfProduct(Long productId);

    @Query("SELECT AVG(r.rating) AS average, COUNT(r) AS count FROM Review r WHERE r.shopId = :shopId")
    RatingAggregate aggregateOfShop(Long shopId);

    interface RatingCount {
        short getRating();

        long getCount();
    }

    interface RatingAggregate {
        Double getAverage();

        long getCount();
    }
}
