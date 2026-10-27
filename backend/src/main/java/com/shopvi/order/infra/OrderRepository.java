package com.shopvi.order.infra;

import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** [ngày (giờ VN), số đơn, GMV của đơn không hủy/trả] theo ngày, từ {@code since}. */
    @Query(value = """
            SELECT CAST(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS day, COUNT(*) AS orders,
                   COALESCE(SUM(CASE WHEN status NOT IN ('CANCELLED', 'RETURNED') THEN total END), 0) AS gmv
            FROM orders WHERE created_at >= :since
            GROUP BY day ORDER BY day""", nativeQuery = true)
    List<Object[]> dailyPlatformStats(Instant since);

    @Query(value = "SELECT status, COUNT(*) FROM orders WHERE created_at >= :since GROUP BY status", nativeQuery = true)
    List<Object[]> countByStatusSince(Instant since);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(Long userId, OrderStatus status, Pageable pageable);

    Optional<Order> findByCodeAndUserId(String code, Long userId);

    /** Khóa dòng đơn khi đổi trạng thái để 2 request hủy đồng thời không hoàn kho 2 lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.code = :code AND o.userId = :userId")
    Optional<Order> findByCodeAndUserIdForUpdate(String code, Long userId);

    List<Order> findByCheckoutIdOrderByIdAsc(UUID checkoutId);

    /** Khóa các đơn của checkout (theo thứ tự id) khi cổng báo thanh toán. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.checkoutId = :checkoutId ORDER BY o.id")
    List<Order> findByCheckoutIdForUpdate(UUID checkoutId);

    /** Như trên nhưng bỏ qua đơn đang bị khóa (SKIP LOCKED) — job hủy đơn quá hạn không chờ IPN đang xử lý. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM Order o WHERE o.checkoutId = :checkoutId ORDER BY o.id")
    List<Order> findByCheckoutIdForUpdateSkipLocked(UUID checkoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.code = :code")
    Optional<Order> findByCodeForUpdate(String code);

    /** Checkout có đơn thanh toán online chưa thanh toán, đặt trước {@code cutoff}. */
    @Query(value = """
            SELECT DISTINCT checkout_id FROM orders
            WHERE status = 'PENDING' AND payment_status = 'UNPAID' AND payment_method <> 'COD' AND created_at < :cutoff
            LIMIT :limit""", nativeQuery = true)
    List<UUID> findOverdueUnpaidCheckouts(Instant cutoff, int limit);

    // ===== Đơn của shop (Seller Center)

    Page<Order> findByShopId(Long shopId, Pageable pageable);

    Page<Order> findByShopIdAndStatus(Long shopId, OrderStatus status, Pageable pageable);

    Optional<Order> findByCodeAndShopId(String code, Long shopId);

    /** Khóa dòng đơn khi seller đổi trạng thái (tránh xung đột với người mua hủy cùng lúc). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.code = :code AND o.shopId = :shopId")
    Optional<Order> findByCodeAndShopIdForUpdate(String code, Long shopId);

    @Query("SELECT o.status AS status, COUNT(o) AS count FROM Order o WHERE o.shopId = :shopId GROUP BY o.status")
    List<StatusCount> countByStatus(Long shopId);

    interface StatusCount {
        OrderStatus getStatus();

        long getCount();
    }
}
