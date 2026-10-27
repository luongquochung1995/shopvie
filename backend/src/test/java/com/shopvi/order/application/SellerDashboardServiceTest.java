package com.shopvi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.application.SellerDashboardService.Granularity;
import com.shopvi.order.application.SellerDashboardService.Point;
import com.shopvi.order.application.SellerDashboardService.Range;
import com.shopvi.order.infra.SellerStatsQueries.PeriodRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SellerDashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void defaultRangeIsLast30DaysOrLast12Months() {
        assertThat(SellerDashboardService.resolveRange(null, null, Granularity.DAY, TODAY))
                .isEqualTo(new Range(LocalDate.of(2026, 9, 27), TODAY, Granularity.DAY));
        assertThat(SellerDashboardService.resolveRange(null, null, Granularity.MONTH, TODAY))
                .isEqualTo(new Range(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 31), Granularity.MONTH));
    }

    @Test
    void monthRangeIsExpandedToWholeMonths() {
        assertThat(SellerDashboardService.resolveRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 3, 3),
                Granularity.MONTH, TODAY))
                .isEqualTo(new Range(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31), Granularity.MONTH));
    }

    @Test
    void rejectsInvertedOrTooLongRanges() {
        assertInvalid(() -> SellerDashboardService.resolveRange(TODAY, TODAY.minusDays(1), Granularity.DAY, TODAY),
                "from");
        assertInvalid(() -> SellerDashboardService.resolveRange(TODAY.minusDays(366), TODAY, Granularity.DAY, TODAY),
                "to");
        assertThat(SellerDashboardService.resolveRange(TODAY.minusDays(365), TODAY, Granularity.DAY, TODAY).from())
                .isEqualTo(TODAY.minusDays(365));
        assertInvalid(() -> SellerDashboardService.resolveRange(TODAY.minusMonths(36), TODAY, Granularity.MONTH, TODAY),
                "to");
    }

    @Test
    void seriesHasEveryPeriodWithZeroForEmptyOnes() {
        Range days = new Range(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4), Granularity.DAY);
        List<Point> series = SellerDashboardService.fillSeries(days, List.of(
                new PeriodRow(LocalDate.of(2026, 10, 2).atStartOfDay(), 500_000, 3, 1)));

        assertThat(series).containsExactly(
                new Point(LocalDate.of(2026, 10, 1), 0, 0),
                new Point(LocalDate.of(2026, 10, 2), 500_000, 3),
                new Point(LocalDate.of(2026, 10, 3), 0, 0),
                new Point(LocalDate.of(2026, 10, 4), 0, 0));

        Range months = new Range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), Granularity.MONTH);
        assertThat(SellerDashboardService.fillSeries(months, List.of())).extracting(Point::period)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    private static void assertInvalid(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(ex.getDetails()).singleElement().extracting("field").isEqualTo(field);
        });
    }
}
