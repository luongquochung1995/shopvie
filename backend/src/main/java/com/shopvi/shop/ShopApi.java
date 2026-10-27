package com.shopvi.shop;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * API công khai của module shop.
 */
public interface ShopApi {

    /** Shop đang ACTIVE và chưa bị xóa mềm. */
    Optional<ShopSummary> findActiveSummary(Long shopId);

    /** Thông tin shop (mọi trạng thái, chưa xóa) — dùng hiển thị giỏ hàng/đơn hàng. */
    Map<Long, ShopSummary> findSummaries(Collection<Long> shopIds);

    /** Tập id shop đang ACTIVE trong danh sách. */
    Set<Long> activeShopIds(Collection<Long> shopIds);

    /** Cập nhật điểm đánh giá trung bình của shop (từ đánh giá sản phẩm). */
    void updateRating(Long shopId, BigDecimal ratingAvg);

    /** Địa chỉ lấy hàng (empty nếu shop chưa khai báo). */
    Optional<PickupAddress> findPickupAddress(Long shopId);

    /** Chủ shop (empty nếu không có shop). */
    Optional<Long> findOwnerId(Long shopId);

    /** Id shop của chủ shop (mọi trạng thái), empty nếu chưa mở shop. */
    Optional<Long> findShopIdOfOwner(Long ownerId);

    /** Id các shop đang bị khóa — sản phẩm của các shop này không hiển thị công khai. */
    Set<Long> bannedShopIds();

    /** Số shop theo trạng thái (thống kê admin). */
    Map<String, Long> countByStatus();

    /** Id shop của seller (mọi trạng thái) — dùng cho thao tác chỉ đọc. Ném {@code SHOP_NOT_FOUND} nếu chưa có shop. */
    Long requireShopIdOfOwner(Long ownerId);

    /**
     * Id shop của seller, yêu cầu shop đang ACTIVE — dùng cho thao tác ghi (đăng bán, xử lý đơn).
     * Ném {@code SHOP_NOT_FOUND} / {@code SHOP_NOT_ACTIVE}.
     */
    Long requireActiveShopIdOfOwner(Long ownerId);
}
