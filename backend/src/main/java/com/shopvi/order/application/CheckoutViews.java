package com.shopvi.order.application;

import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.promotion.PromotionApi.AppliedVoucher;
import com.shopvi.shop.ShopSummary;
import com.shopvi.user.AddressSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dữ liệu vào/ra của checkout. Tiền tệ: VND. */
public final class CheckoutViews {

    private CheckoutViews() {
    }

    /**
     * @param available còn bán và đủ hàng cho số lượng trong giỏ
     */
    public record Line(Long variantId, Long productId, String productName, String productSlug, String variantName,
                       String imageUrl, long unitPrice, int quantity, int stock, boolean available, long lineTotal) {
    }

    /** @param vouchers voucher áp cho shop (voucher shop + phần phân bổ của voucher sàn) */
    public record ShopGroup(ShopSummary shop, List<Line> items, long subtotal, long shippingFee, long discount,
                            long total, List<AppliedVoucher> vouchers) {
    }

    /**
     * @param address          địa chỉ được chọn (mặc định nếu không truyền addressId); null nếu chưa có địa chỉ
     * @param hasUnavailableItems có món không đặt được — FE yêu cầu người dùng quay lại giỏ hàng
     * @param voucherCodes     mã voucher đã áp thành công
     */
    public record Preview(AddressSnapshot address, List<ShopGroup> groups, long subtotal, long shippingFee,
                          long discount, long total, List<String> paymentMethods, boolean hasUnavailableItems,
                          List<String> voucherCodes) {
    }

    /**
     * @param notes         lời nhắn cho từng shop (shopId → nội dung)
     * @param expectedTotal tổng tiền người dùng đã xem ở bước preview; khác thực tế → PRICE_CHANGED
     */
    public record PlaceOrderCommand(Long addressId, String paymentMethod, Map<Long, String> notes, Long expectedTotal,
                                    List<String> voucherCodes) {
    }

    public record PlacedOrder(String code, Long shopId, long total) {
    }

    /**
     * @param paymentUrl       URL cổng thanh toán (null với COD) — frontend chuyển người mua tới đây
     * @param paymentExpiresAt hạn thanh toán (null với COD)
     */
    public record PlaceOrderResult(UUID checkoutId, List<PlacedOrder> orders, long total, String paymentUrl,
                                   Instant paymentExpiresAt) {
    }

    public enum CheckoutPaymentStatus {
        /** COD: không cần thanh toán online. */
        NOT_REQUIRED,
        /** Đang chờ người mua thanh toán / chờ cổng báo kết quả. */
        PENDING,
        /** Giao dịch gần nhất thất bại; vẫn thanh toán lại được tới hạn. */
        FAILED,
        PAID,
        /** Đơn đã bị hủy (quá hạn thanh toán hoặc người mua hủy). */
        CANCELLED
    }

    /** @param expiresAt hạn thanh toán (chỉ có khi PENDING/FAILED) */
    public record CheckoutPayment(UUID checkoutId, PaymentMethod paymentMethod, CheckoutPaymentStatus status,
                                  long total, Instant expiresAt, List<String> orderCodes) {
    }
}
