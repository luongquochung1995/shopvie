package com.shopvi.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** API công khai của module order cho module khác (đánh giá sản phẩm, thống kê admin). */
public interface OrderApi {

    /** Dòng đơn của người mua thuộc đơn đã hoàn thành (COMPLETED) — điều kiện để đánh giá. */
    Optional<CompletedItem> findCompletedItem(Long userId, String orderCode, Long orderItemId);

    /** Id các dòng của đơn thuộc người mua (rỗng nếu không có đơn). */
    List<Long> itemIdsOfOrder(Long userId, String orderCode);

    /** Thống kê đơn toàn sàn từ ngày {@code from} (giờ VN, gồm cả ngày đó) tới hôm nay. */
    PlatformOrderStats platformStats(LocalDate from);

    /**
     * @param gmv     tổng tiền hàng + phí ship của đơn không bị hủy/trả trong kỳ
     * @param byStatus số đơn trong kỳ theo trạng thái
     * @param daily   từng ngày trong kỳ (đủ mọi ngày, ngày không có đơn = 0)
     */
    record PlatformOrderStats(long orders, long gmv, Map<String, Long> byStatus, List<DailyPoint> daily) {
    }

    record DailyPoint(LocalDate date, long orders, long gmv) {
    }

    /** Snapshot dòng đơn đã hoàn thành. */
    record CompletedItem(Long orderItemId, String orderCode, Long productId, Long shopId, String productName,
                         String variantName) {
    }
}
