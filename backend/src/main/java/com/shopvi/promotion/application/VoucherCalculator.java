package com.shopvi.promotion.application;

import com.shopvi.promotion.PromotionApi.AppliedVoucher;
import com.shopvi.promotion.PromotionApi.ShopAmount;
import com.shopvi.promotion.PromotionApi.VoucherQuote;
import com.shopvi.promotion.domain.Voucher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tính giảm giá đã kiểm tra điều kiện xong:
 * <ol>
 *   <li>Voucher shop: tính trên tiền hàng / phí ship của shop đó.</li>
 *   <li>Voucher sàn: tính trên phần còn lại sau voucher shop (tiền hàng, hoặc phí ship với FREESHIP), rồi phân bổ cho
 *       từng shop theo tỷ lệ phần còn lại — phần dư do làm tròn cộng vào shop cuối cùng có giá trị.</li>
 * </ol>
 * Mỗi đơn không bao giờ được giảm quá tiền hàng + phí ship.
 */
final class VoucherCalculator {

    private VoucherCalculator() {
    }

    static VoucherQuote calculate(List<ShopAmount> groups, Map<Long, Voucher> shopVouchers, Voucher platform) {
        Map<Long, List<AppliedVoucher>> byShop = new LinkedHashMap<>();
        Map<Long, long[]> remaining = new LinkedHashMap<>(); // shopId → {tiền hàng còn lại, phí ship còn lại}
        for (ShopAmount g : groups) {
            List<AppliedVoucher> applied = new ArrayList<>();
            long merchandise = g.subtotal();
            long shipping = g.shippingFee();
            Voucher v = shopVouchers.get(g.shopId());
            if (v != null) {
                long amount = v.discountFor(merchandise, shipping);
                applied.add(applied(v, "SHOP", amount));
                if (v.getType() == Voucher.Type.FREESHIP) {
                    shipping -= amount;
                } else {
                    merchandise -= amount;
                }
            }
            byShop.put(g.shopId(), applied);
            remaining.put(g.shopId(), new long[]{merchandise, shipping});
        }
        if (platform != null) {
            int part = platform.getType() == Voucher.Type.FREESHIP ? 1 : 0;
            long base = remaining.values().stream().mapToLong(r -> r[part]).sum();
            long total = part == 1 ? platform.discountFor(0, base) : platform.discountFor(base, 0);
            allocate(total, base, part, remaining, byShop, platform);
        }
        return new VoucherQuote(byShop);
    }

    /** Phân bổ {@code total} theo tỷ lệ phần còn lại của từng shop. */
    private static void allocate(long total, long base, int part, Map<Long, long[]> remaining,
                                 Map<Long, List<AppliedVoucher>> byShop, Voucher platform) {
        if (total <= 0 || base <= 0) {
            return;
        }
        List<Long> eligible = remaining.entrySet().stream().filter(e -> e.getValue()[part] > 0).map(Map.Entry::getKey).toList();
        long allocated = 0;
        for (int i = 0; i < eligible.size(); i++) {
            Long shopId = eligible.get(i);
            long cap = remaining.get(shopId)[part];
            long share = i == eligible.size() - 1 ? total - allocated : total * cap / base;
            share = Math.min(share, cap);
            allocated += share;
            if (share > 0) {
                byShop.get(shopId).add(applied(platform, "PLATFORM", share));
            }
        }
    }

    private static AppliedVoucher applied(Voucher v, String scope, long amount) {
        return new AppliedVoucher(v.getId(), v.getCode(), v.getType().name(), scope, amount);
    }
}
