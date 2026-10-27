package com.shopvi.order.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.OrderApi;
import com.shopvi.order.application.OrderViews.OrderDetail;
import com.shopvi.order.application.OrderViews.OrderSummary;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.payment.PaymentApi;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Đơn mua của người mua: xem danh sách/chi tiết (kèm hành trình vận chuyển), hủy đơn. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService implements OrderApi {

    private final OrderRepository orderRepository;
    private final ShopApi shopApi;
    private final OrderStockReleaser stockReleaser;
    private final OrderEvents orderEvents;
    private final PaymentApi paymentApi;
    private final ShippingApi shippingApi;
    private final Clock clock;

    public Page<OrderSummary> list(Long userId, OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        Map<Long, ShopSummary> shops = shopApi.findSummaries(page.getContent().stream().map(Order::getShopId).toList());
        List<OrderSummary> items = page.getContent().stream()
                .map(o -> OrderSummary.of(o, shops.get(o.getShopId()), paymentApi.paymentTtl()))
                .toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    public OrderDetail detail(Long userId, String code) {
        Order order = orderRepository.findByCodeAndUserId(code, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return toDetail(order);
    }

    /**
     * Hủy đơn (chỉ khi PENDING/CONFIRMED) và hoàn kho trong cùng transaction; đơn đã có vận đơn thì hủy vận đơn,
     * đơn đã thanh toán online chuyển sang hoàn tiền.
     */
    @Transactional
    public OrderDetail cancel(Long userId, String code, String reason) {
        Order order = orderRepository.findByCodeAndUserIdForUpdate(code, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        String note = StringUtils.hasText(reason) ? reason.trim() : null;
        orderEvents.transition(order, OrderStatus.CANCELLED, userId, note);
        stockReleaser.release(order);
        shippingApi.cancelShipment(code, note != null ? note : "Người mua hủy đơn");
        orderRepository.flush();
        return toDetail(order);
    }

    /** Người mua xác nhận đã nhận hàng (DELIVERED → COMPLETED); sau đó đánh giá được sản phẩm. */
    @Transactional
    public OrderDetail complete(Long userId, String code) {
        Order order = orderRepository.findByCodeAndUserIdForUpdate(code, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        orderEvents.transition(order, OrderStatus.COMPLETED, userId, null);
        orderRepository.flush();
        return toDetail(order);
    }

    @Override
    public Optional<CompletedItem> findCompletedItem(Long userId, String orderCode, Long orderItemId) {
        return orderRepository.findByCodeAndUserId(orderCode, userId)
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .flatMap(o -> o.getItems().stream().filter(i -> i.getId().equals(orderItemId)).findFirst()
                        .map(i -> new CompletedItem(i.getId(), o.getCode(), i.getProductId(), o.getShopId(),
                                i.getProductName(), i.getVariantName())));
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformOrderStats platformStats(LocalDate from) {
        Instant since = from.atStartOfDay(SellerDashboardService.ZONE).toInstant();
        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(LocalDate.now(clock.withZone(SellerDashboardService.ZONE))); d = d.plusDays(1)) {
            byDay.put(d, new DailyPoint(d, 0, 0));
        }
        for (Object[] row : orderRepository.dailyPlatformStats(since)) {
            LocalDate day = ((Date) row[0]).toLocalDate();
            byDay.put(day, new DailyPoint(day, ((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        orderRepository.countByStatusSince(since).forEach(r -> byStatus.put((String) r[0], ((Number) r[1]).longValue()));
        List<DailyPoint> daily = List.copyOf(byDay.values());
        return new PlatformOrderStats(daily.stream().mapToLong(DailyPoint::orders).sum(),
                daily.stream().mapToLong(DailyPoint::gmv).sum(), byStatus, daily);
    }

    @Override
    public List<Long> itemIdsOfOrder(Long userId, String orderCode) {
        return orderRepository.findByCodeAndUserId(orderCode, userId)
                .map(o -> o.getItems().stream().map(i -> i.getId()).toList())
                .orElse(List.of());
    }

    private OrderDetail toDetail(Order order) {
        ShopSummary shop = shopApi.findSummaries(List.of(order.getShopId())).get(order.getShopId());
        return OrderDetail.of(order, shop, paymentApi.paymentTtl(),
                shippingApi.findByOrderCode(order.getCode()).orElse(null));
    }
}
