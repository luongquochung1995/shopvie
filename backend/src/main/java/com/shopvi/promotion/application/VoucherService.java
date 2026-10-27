package com.shopvi.promotion.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.promotion.PromotionApi;
import com.shopvi.promotion.domain.Voucher;
import com.shopvi.promotion.domain.VoucherUsage;
import com.shopvi.promotion.infra.VoucherRepository;
import com.shopvi.promotion.infra.VoucherUsageRepository;
import com.shopvi.promotion.infra.VoucherUsageRepository.UsageCount;
import com.shopvi.shop.ShopApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Voucher sàn & voucher shop (P5-4). Áp tối đa 1 voucher sàn + 1 voucher mỗi shop; voucher shop tính trước,
 * voucher sàn tính trên phần còn lại và được phân bổ cho các đơn theo tỷ lệ. Lượt dùng tăng bằng câu UPDATE nguyên tử
 * (không vượt {@code usage_limit} khi nhiều người dùng cùng lúc).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService implements PromotionApi {

    /** Lý do voucher không áp được (trả về FE trong {@code error.details[].message}). */
    public enum Reason { NOT_STARTED, EXPIRED, DISABLED, EXHAUSTED, USER_LIMIT, MIN_ORDER, SHOP_NOT_IN_CART, DUPLICATE }

    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository usageRepository;
    private final ShopApi shopApi;
    private final Clock clock;

    // ===== Checkout

    @Override
    public VoucherQuote quoteVouchers(Long userId, Collection<String> rawCodes, List<ShopAmount> groups) {
        List<String> codes = normalize(rawCodes);
        if (codes.isEmpty()) {
            return VoucherQuote.empty();
        }
        Map<String, Voucher> byCode = voucherRepository.findByCodes(codes).stream()
                .collect(Collectors.toMap(v -> v.getCode().toUpperCase(Locale.ROOT), v -> v));
        List<ErrorDetail> missing = codes.stream().filter(c -> !byCode.containsKey(c))
                .map(c -> new ErrorDetail(c, "NOT_FOUND")).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, ErrorCode.VOUCHER_NOT_FOUND.getDefaultMessage(), missing);
        }
        List<Voucher> vouchers = codes.stream().map(byCode::get).toList();
        Map<Long, Long> used = usedByUser(userId, vouchers);
        Instant now = clock.instant();
        Map<Long, ShopAmount> shops = groups.stream().collect(Collectors.toMap(ShopAmount::shopId, g -> g, (a, b) -> a,
                LinkedHashMap::new));

        List<ErrorDetail> problems = new ArrayList<>();
        Voucher platform = null;
        Map<Long, Voucher> shopVouchers = new HashMap<>();
        for (Voucher v : vouchers) {
            Reason reason = basicProblem(v, now, used.getOrDefault(v.getId(), 0L));
            if (reason == null && v.isPlatform()) {
                long merchandise = groups.stream().mapToLong(ShopAmount::subtotal).sum();
                reason = platform != null ? Reason.DUPLICATE : merchandise < v.getMinOrderValue() ? Reason.MIN_ORDER : null;
                if (reason == null) {
                    platform = v;
                }
            } else if (reason == null) {
                ShopAmount group = shops.get(v.getShopId());
                reason = group == null ? Reason.SHOP_NOT_IN_CART
                        : shopVouchers.containsKey(v.getShopId()) ? Reason.DUPLICATE
                        : group.subtotal() < v.getMinOrderValue() ? Reason.MIN_ORDER : null;
                if (reason == null) {
                    shopVouchers.put(v.getShopId(), v);
                }
            }
            if (reason != null) {
                problems.add(new ErrorDetail(v.getCode(), reason.name()));
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                    ErrorCode.VOUCHER_NOT_APPLICABLE.getDefaultMessage(), problems);
        }
        return VoucherCalculator.calculate(new ArrayList<>(shops.values()), shopVouchers, platform);
    }

    @Override
    @Transactional
    public void redeemVouchers(Long userId, UUID checkoutId, List<Redemption> redemptions) {
        Instant now = clock.instant();
        // Theo thứ tự id voucher để các checkout đồng thời khóa dòng cùng thứ tự (tránh deadlock)
        Map<Long, List<Redemption>> byVoucher = new TreeMap<>(redemptions.stream()
                .collect(Collectors.groupingBy(Redemption::voucherId)));
        for (Map.Entry<Long, List<Redemption>> e : byVoucher.entrySet()) {
            Voucher voucher = voucherRepository.findById(e.getKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
            if (voucherRepository.incrementUsage(voucher.getId(), now) == 0) {
                throw new BusinessException(ErrorCode.VOUCHER_EXHAUSTED, ErrorCode.VOUCHER_EXHAUSTED.getDefaultMessage(),
                        List.of(new ErrorDetail(voucher.getCode(), Reason.EXHAUSTED.name())));
            }
            // Dòng voucher đã bị khóa bởi UPDATE ở trên nên đếm lượt của người dùng ở đây không bị đua
            if (usageRepository.countCheckouts(voucher.getId(), userId) >= voucher.getPerUserLimit()) {
                throw new BusinessException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                        ErrorCode.VOUCHER_NOT_APPLICABLE.getDefaultMessage(),
                        List.of(new ErrorDetail(voucher.getCode(), Reason.USER_LIMIT.name())));
            }
            e.getValue().forEach(r -> usageRepository.save(
                    VoucherUsage.of(voucher.getId(), userId, r.orderId(), checkoutId, r.amount())));
        }
    }

    @Override
    @Transactional
    public void releaseVouchers(Long orderId) {
        List<VoucherUsage> usages = usageRepository.findByOrderId(orderId);
        if (usages.isEmpty()) {
            return;
        }
        usageRepository.deleteAll(usages);
        usageRepository.flush();
        usages.stream().sorted(Comparator.comparing(VoucherUsage::getVoucherId)).forEach(u -> {
            if (!usageRepository.existsByVoucherIdAndCheckoutId(u.getVoucherId(), u.getCheckoutId())) {
                voucherRepository.decrementUsage(u.getVoucherId());
            }
        });
    }

    /** Voucher người mua còn dùng được cho các shop trong giỏ (để chọn ở trang thanh toán). */
    public List<VoucherView> available(Long userId, Collection<Long> shopIds) {
        List<Voucher> running = voucherRepository.findRunning(shopIds.isEmpty() ? Set.of(-1L) : shopIds, clock.instant());
        Map<Long, Long> used = usedByUser(userId, running);
        return running.stream().filter(v -> used.getOrDefault(v.getId(), 0L) < v.getPerUserLimit())
                .map(VoucherView::of).toList();
    }

    // ===== Quản lý (seller / admin)

    @Transactional
    public VoucherView createForShop(Long sellerId, VoucherCommand cmd) {
        return VoucherView.of(create(shopApi.requireActiveShopIdOfOwner(sellerId), cmd));
    }

    @Transactional
    public VoucherView createPlatform(VoucherCommand cmd) {
        return VoucherView.of(create(null, cmd));
    }

    public Page<VoucherView> listOfShop(Long sellerId, Pageable pageable) {
        return voucherRepository.findByShopId(shopApi.requireShopIdOfOwner(sellerId), pageable).map(VoucherView::of);
    }

    public Page<VoucherView> listPlatform(Pageable pageable) {
        return voucherRepository.findByShopIdIsNull(pageable).map(VoucherView::of);
    }

    @Transactional
    public VoucherView disableOfShop(Long sellerId, Long voucherId) {
        Voucher v = voucherRepository.findByIdAndShopId(voucherId, shopApi.requireShopIdOfOwner(sellerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
        v.disable();
        return VoucherView.of(v);
    }

    @Transactional
    public VoucherView disablePlatform(Long voucherId) {
        Voucher v = voucherRepository.findById(voucherId).filter(Voucher::isPlatform)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
        v.disable();
        return VoucherView.of(v);
    }

    private Voucher create(Long shopId, VoucherCommand cmd) {
        String code = cmd.code().trim().toUpperCase(Locale.ROOT);
        List<ErrorDetail> errors = new ArrayList<>();
        if (!cmd.endAt().isAfter(cmd.startAt())) {
            errors.add(new ErrorDetail("endAt", "must be after startAt"));
        }
        if (!cmd.endAt().isAfter(clock.instant())) {
            errors.add(new ErrorDetail("endAt", "must be in the future"));
        }
        if (cmd.type() == Voucher.Type.PERCENT && cmd.value() > 100) {
            errors.add(new ErrorDetail("value", "percent must be between 1 and 100"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), errors);
        }
        if (voucherRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.VOUCHER_CODE_EXISTS);
        }
        return voucherRepository.save(Voucher.create(shopId, code, cmd.name().trim(), cmd.type(), cmd.value(),
                cmd.minOrderValue(), cmd.maxDiscount(), cmd.usageLimit(), cmd.perUserLimit(), cmd.startAt(),
                cmd.endAt()));
    }

    // ===== helpers

    private static Reason basicProblem(Voucher v, Instant now, long usedByUser) {
        if (v.getStatus() == Voucher.Status.DISABLED) {
            return Reason.DISABLED;
        }
        if (now.isBefore(v.getStartAt())) {
            return Reason.NOT_STARTED;
        }
        if (!now.isBefore(v.getEndAt())) {
            return Reason.EXPIRED;
        }
        if (v.getUsedCount() >= v.getUsageLimit()) {
            return Reason.EXHAUSTED;
        }
        return usedByUser >= v.getPerUserLimit() ? Reason.USER_LIMIT : null;
    }

    private Map<Long, Long> usedByUser(Long userId, List<Voucher> vouchers) {
        if (vouchers.isEmpty()) {
            return Map.of();
        }
        return usageRepository.countCheckouts(userId, vouchers.stream().map(Voucher::getId).toList()).stream()
                .collect(Collectors.toMap(UsageCount::getVoucherId, UsageCount::getCount));
    }

    static List<String> normalize(Collection<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream().filter(StringUtils::hasText).map(c -> c.trim().toUpperCase(Locale.ROOT))
                .distinct().toList();
    }

    public record VoucherCommand(String code, String name, Voucher.Type type, long value, long minOrderValue,
                                 Long maxDiscount, int usageLimit, int perUserLimit, Instant startAt, Instant endAt) {
    }

    /** @param shopId null = voucher sàn */
    public record VoucherView(Long id, Long shopId, String code, String name, Voucher.Type type, long value,
                              long minOrderValue, Long maxDiscount, int usageLimit, int usedCount, int perUserLimit,
                              Instant startAt, Instant endAt, Voucher.Status status) {
        static VoucherView of(Voucher v) {
            return new VoucherView(v.getId(), v.getShopId(), v.getCode(), v.getName(), v.getType(), v.getValue(),
                    v.getMinOrderValue(), v.getMaxDiscount(), v.getUsageLimit(), v.getUsedCount(), v.getPerUserLimit(),
                    v.getStartAt(), v.getEndAt(), v.getStatus());
        }
    }
}
