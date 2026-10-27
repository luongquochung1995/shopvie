package com.shopvi.shipping;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * API công khai của module shipping: tính phí ship, tạo/hủy vận đơn, hành trình vận chuyển.
 * Dùng GHN khi đã cấu hình, ngược lại (hoặc GHN lỗi khi tính phí) dùng bảng phí nội bộ.
 * Cập nhật trạng thái từ hãng được báo qua sự kiện {@link ShipmentProgressed}.
 */
public interface ShippingApi {

    /** Phí vận chuyển (VND) cho một kiện hàng. Không bao giờ lỗi vì có bảng phí nội bộ dự phòng. */
    long quote(QuoteRequest request);

    /** Tạo vận đơn khi seller xác nhận đơn. Ném {@code SHIPPING_PROVIDER_ERROR} nếu hãng vận chuyển lỗi. */
    ShipmentView createShipment(CreateShipmentRequest request);

    /** Seller tự cập nhật trạng thái (vận đơn nội bộ, hoặc khi chưa nhận được webhook của hãng). */
    void recordManualStatus(String orderCode, ShipmentStatus status, String note);

    /** Hủy vận đơn khi đơn bị hủy sau khi đã xác nhận (không có vận đơn thì bỏ qua). */
    void cancelShipment(String orderCode, String reason);

    Optional<ShipmentView> findByOrderCode(String orderCode);

    Map<String, ShipmentView> findByOrderCodes(Collection<String> orderCodes);

    /**
     * @param origin     nơi lấy hàng (null → mặc định cấu hình)
     * @param itemCount  số sản phẩm (khối lượng ước tính theo cấu hình mỗi sản phẩm)
     * @param itemsValue giá trị hàng (VND) — dùng cho bảo hiểm của hãng
     */
    record QuoteRequest(Location origin, Location destination, int itemCount, long itemsValue) {
    }

    /**
     * @param codAmount tiền shipper thu hộ (0 nếu đã thanh toán online)
     */
    record CreateShipmentRequest(String orderCode, Party from, Party to, int itemCount, long itemsValue,
                                 long codAmount, String note, Collection<Item> items) {
    }

    record Item(String name, int quantity) {
    }
}
