package com.shopvi.promotion;

import java.util.UUID;

/** API công khai flash sale cho nơi tạo đơn (consumer Kafka ở module order). */
public interface FlashSaleApi {

    /** Lượt giữ hàng đã được xử lý xong (CONFIRMED hoặc FAILED) — consumer nhận lại bản tin thì bỏ qua. */
    boolean isProcessed(UUID reservationId);

    /** Đơn đã tạo: cộng {@code sold}, ghi lượt giữ CONFIRMED (cùng transaction tạo đơn). */
    void confirmReservation(FlashSaleOrderRequested request, String orderCode);

    /** Không tạo được đơn: trả quota trên Redis và ghi lượt giữ FAILED kèm lý do (mã lỗi). */
    void failReservation(FlashSaleOrderRequested request, String reason);
}
