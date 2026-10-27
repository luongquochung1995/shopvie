package com.shopvi.order.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.infra.SellerStatsQueries;
import com.shopvi.order.infra.SellerStatsQueries.PeriodRow;
import com.shopvi.shop.ShopApi;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard của seller (P3-4): doanh số theo ngày/tháng, số đơn theo trạng thái, sản phẩm bán chạy.
 * Ngày được tính theo giờ Việt Nam; doanh số = tổng tiền hàng (không gồm phí ship) của đơn chưa hủy/trả hàng,
 * tính theo ngày đặt đơn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerDashboardService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_DAYS = 366;
    static final int MAX_MONTHS = 36;
    static final int TOP_PRODUCTS = 5;

    private final ShopApi shopApi;
    private final SellerStatsQueries stats;
    private final SellerOrderService sellerOrderService;
    private final Clock clock;

    public enum Granularity { DAY, MONTH }

    public Dashboard dashboard(Long sellerId, LocalDate from, LocalDate to, Granularity granularity) {
        Long shopId = shopApi.requireShopIdOfOwner(sellerId);
        Range range = resolveRange(from, to, granularity == null ? Granularity.DAY : granularity,
                LocalDate.now(clock.withZone(ZONE)));
        var start = range.from().atStartOfDay(ZONE).toInstant();
        var end = range.to().plusDays(1).atStartOfDay(ZONE).toInstant();

        List<PeriodRow> rows = stats.periods(shopId, start, end,
                range.granularity() == Granularity.DAY ? "day" : "month", ZONE);
        List<Point> series = fillSeries(range, rows);
        Summary summary = new Summary(
                series.stream().mapToLong(Point::revenue).sum(),
                series.stream().mapToLong(Point::orders).sum(),
                rows.stream().mapToLong(PeriodRow::cancelled).sum(),
                stats.unitsSold(shopId, start, end));
        List<TopProduct> top = stats.topProducts(shopId, start, end, TOP_PRODUCTS).stream()
                .map(r -> new TopProduct(r.productId(), r.productName(), r.productSlug(), r.imageUrl(), r.quantity(),
                        r.revenue()))
                .toList();
        return new Dashboard(range.from(), range.to(), range.granularity(), ZONE.getId(), summary, series,
                sellerOrderService.countByStatusOfShop(shopId), top);
    }

    /**
     * Mặc định 30 ngày gần nhất (DAY) hoặc 12 tháng gần nhất (MONTH). Với MONTH, khoảng được mở rộng ra trọn tháng.
     */
    static Range resolveRange(LocalDate from, LocalDate to, Granularity granularity, LocalDate today) {
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from
                : granularity == Granularity.DAY ? end.minusDays(29) : end.withDayOfMonth(1).minusMonths(11);
        if (granularity == Granularity.MONTH) {
            start = start.withDayOfMonth(1);
            end = end.withDayOfMonth(end.lengthOfMonth());
        }
        if (start.isAfter(end)) {
            throw invalid("from", "must not be after to");
        }
        if (granularity == Granularity.DAY && ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw invalid("to", "range must not exceed " + MAX_DAYS + " days");
        }
        if (granularity == Granularity.MONTH && ChronoUnit.MONTHS.between(start, end) + 1 > MAX_MONTHS) {
            throw invalid("to", "range must not exceed " + MAX_MONTHS + " months");
        }
        return new Range(start, end, granularity);
    }

    /** Đủ mọi kỳ trong khoảng (kỳ không có đơn = 0) để vẽ biểu đồ liền mạch. */
    static List<Point> fillSeries(Range range, List<PeriodRow> rows) {
        Map<LocalDate, PeriodRow> byPeriod = new HashMap<>();
        rows.forEach(r -> byPeriod.put(r.period().toLocalDate(), r));
        List<Point> series = new ArrayList<>();
        for (LocalDate p = range.from(); !p.isAfter(range.to());
             p = range.granularity() == Granularity.DAY ? p.plusDays(1) : p.plusMonths(1)) {
            PeriodRow r = byPeriod.get(p);
            series.add(r == null ? new Point(p, 0, 0) : new Point(p, r.revenue(), r.orders()));
        }
        return series;
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                List.of(new ErrorDetail(field, message)));
    }

    record Range(LocalDate from, LocalDate to, Granularity granularity) {
    }

    /** @param period ngày đầu kỳ (ngày, hoặc ngày 1 của tháng) */
    public record Point(LocalDate period, long revenue, long orders) {
    }

    /**
     * @param revenue         doanh số trong khoảng (VND)
     * @param orders          số đơn (không tính đơn hủy/trả hàng)
     * @param cancelledOrders số đơn đã hủy
     * @param unitsSold       số sản phẩm đã bán
     */
    public record Summary(long revenue, long orders, long cancelledOrders, long unitsSold) {
    }

    public record TopProduct(Long productId, String productName, String productSlug, String imageUrl, long quantity,
                             long revenue) {
    }

    /** @param statusCounts số đơn theo trạng thái (toàn thời gian) — việc cần xử lý */
    public record Dashboard(LocalDate from, LocalDate to, Granularity granularity, String timezone, Summary summary,
                            List<Point> series, Map<OrderStatus, Long> statusCounts, List<TopProduct> topProducts) {
    }
}
