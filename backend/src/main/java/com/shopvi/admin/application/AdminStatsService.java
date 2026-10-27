package com.shopvi.admin.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.order.OrderApi;
import com.shopvi.order.OrderApi.PlatformOrderStats;
import com.shopvi.shop.ShopApi;
import com.shopvi.user.UserApi;
import com.shopvi.user.UserStats;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Tổng quan toàn sàn cho admin: người dùng, shop, sản phẩm, đơn hàng / GMV theo ngày. */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserApi userApi;
    private final ShopApi shopApi;
    private final CatalogApi catalogApi;
    private final OrderApi orderApi;
    private final Clock clock;

    /** @param days số ngày gần nhất (gồm hôm nay) */
    public Overview overview(int days) {
        LocalDate from = LocalDate.now(clock.withZone(ZONE)).minusDays(days - 1L);
        return new Overview(days, userApi.stats(from.atStartOfDay(ZONE).toInstant()), shopApi.countByStatus(),
                catalogApi.countProductsByStatus(), orderApi.platformStats(from));
    }

    public record Overview(int days, UserStats users, Map<String, Long> shops, Map<String, Long> products,
                           PlatformOrderStats orders) {
    }
}
