package com.shopvi.cart.infra;

import com.shopvi.cart.domain.CartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** Món mới thêm lên đầu. */
    List<CartItem> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<CartItem> findByUserIdAndSelectedTrue(Long userId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    Optional<CartItem> findByUserIdAndVariantId(Long userId, Long variantId);

    long countByUserId(Long userId);

    List<CartItem> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.userId = :userId AND c.variantId IN :variantIds")
    int deleteByUserIdAndVariantIdIn(Long userId, Collection<Long> variantIds);
}
