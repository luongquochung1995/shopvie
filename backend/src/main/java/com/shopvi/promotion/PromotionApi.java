package com.shopvi.promotion;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API công khai của module promotion cho checkout / đơn hàng. */
public interface PromotionApi {

    /**
     * Tính giảm giá của các mã voucher cho một lần checkout (tối đa 1 voucher sàn + 1 voucher mỗi shop).
     * Ném {@code VOUCHER_NOT_FOUND} / {@code VOUCHER_NOT_APPLICABLE} (chi tiết: mã + lý do) nếu có mã không dùng được.
     */
    VoucherQuote quoteVouchers(Long userId, Collection<String> codes, List<ShopAmount> groups);

    /**
     * Ghi nhận lượt dùng khi đặt hàng (trong transaction đặt hàng): tăng {@code used_count} nguyên tử theo
     * {@code usage_limit} và kiểm tra giới hạn mỗi người. Ném {@code VOUCHER_EXHAUSTED} nếu hết lượt.
     */
    void redeemVouchers(Long userId, UUID checkoutId, List<Redemption> redemptions);

    /** Trả lượt dùng khi đơn bị hủy (voucher sàn chỉ hoàn lượt khi mọi đơn của checkout đều hủy). */
    void releaseVouchers(Long orderId);

    /** Tiền hàng và phí ship của một shop trong checkout (VND). */
    record ShopAmount(Long shopId, long subtotal, long shippingFee) {
    }

    /**
     * @param scope  PLATFORM | SHOP
     * @param amount số tiền giảm phân bổ cho đơn (VND)
     */
    record AppliedVoucher(Long voucherId, String code, String type, String scope, long amount) {
    }

    /** Giảm giá theo shop (đã phân bổ phần của voucher sàn). */
    record VoucherQuote(Map<Long, List<AppliedVoucher>> byShop) {

        public long discountOf(Long shopId) {
            return byShop.getOrDefault(shopId, List.of()).stream().mapToLong(AppliedVoucher::amount).sum();
        }

        public static VoucherQuote empty() {
            return new VoucherQuote(Map.of());
        }
    }

    /** Lượt dùng voucher của một đơn vừa tạo. */
    record Redemption(Long voucherId, Long orderId, long amount) {
    }
}
