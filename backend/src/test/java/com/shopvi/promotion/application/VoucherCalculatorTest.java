package com.shopvi.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.promotion.PromotionApi.AppliedVoucher;
import com.shopvi.promotion.PromotionApi.ShopAmount;
import com.shopvi.promotion.PromotionApi.VoucherQuote;
import com.shopvi.promotion.domain.Voucher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VoucherCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    void percentVoucherIsCappedByMaxDiscount() {
        Voucher v = voucher(1L, Voucher.Type.PERCENT, 10, 30_000L);
        assertThat(v.discountFor(200_000, 30_000)).isEqualTo(20_000);
        assertThat(v.discountFor(500_000, 30_000)).isEqualTo(30_000);
    }

    @Test
    void fixedVoucherNeverExceedsMerchandiseAndFreeshipNeverExceedsShipping() {
        assertThat(voucher(null, Voucher.Type.FIXED, 50_000, null).discountFor(30_000, 20_000)).isEqualTo(30_000);
        assertThat(voucher(null, Voucher.Type.FREESHIP, 40_000, null).discountFor(100_000, 25_000)).isEqualTo(25_000);
    }

    @Test
    void platformVoucherAppliesAfterShopVoucherAndIsAllocatedProportionally() {
        List<ShopAmount> groups = List.of(new ShopAmount(1L, 300_000, 30_000), new ShopAmount(2L, 100_000, 22_000));
        Voucher shop1 = voucher(1L, Voucher.Type.FIXED, 100_000, null);
        Voucher platform = voucher(null, Voucher.Type.FIXED, 30_000, null);

        VoucherQuote quote = VoucherCalculator.calculate(groups, Map.of(1L, shop1), platform);

        // Còn lại sau voucher shop: shop 1 = 200k, shop 2 = 100k → voucher sàn 30k chia 20k / 10k
        assertThat(amounts(quote, 1L)).containsExactly(100_000L, 20_000L);
        assertThat(amounts(quote, 2L)).containsExactly(10_000L);
        assertThat(quote.discountOf(1L) + quote.discountOf(2L)).isEqualTo(130_000);
    }

    @Test
    void roundingRemainderGoesToLastShop() {
        List<ShopAmount> groups = List.of(new ShopAmount(1L, 100_000, 0), new ShopAmount(2L, 100_000, 0),
                new ShopAmount(3L, 100_000, 0));
        VoucherQuote quote = VoucherCalculator.calculate(groups, Map.of(),
                voucher(null, Voucher.Type.FIXED, 10_000, null));
        assertThat(quote.discountOf(1L)).isEqualTo(3_333);
        assertThat(quote.discountOf(2L)).isEqualTo(3_333);
        assertThat(quote.discountOf(3L)).isEqualTo(3_334);
    }

    @Test
    void freeshipPlatformVoucherIsAllocatedOnShippingFees() {
        List<ShopAmount> groups = List.of(new ShopAmount(1L, 100_000, 30_000), new ShopAmount(2L, 50_000, 10_000));
        VoucherQuote quote = VoucherCalculator.calculate(groups, Map.of(),
                voucher(null, Voucher.Type.FREESHIP, 20_000, null));
        assertThat(quote.discountOf(1L)).isEqualTo(15_000);
        assertThat(quote.discountOf(2L)).isEqualTo(5_000);
    }

    private static List<Long> amounts(VoucherQuote quote, Long shopId) {
        return quote.byShop().get(shopId).stream().map(AppliedVoucher::amount).toList();
    }

    private static Voucher voucher(Long shopId, Voucher.Type type, long value, Long maxDiscount) {
        return Voucher.create(shopId, "CODE" + type, "Test", type, value, 0, maxDiscount, 100, 1,
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }
}
