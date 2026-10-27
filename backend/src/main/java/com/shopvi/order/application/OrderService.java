package com.shopvi.order.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.application.OrderViews.OrderDetail;
import com.shopvi.order.application.OrderViews.OrderSummary;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.payment.PaymentApi;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.util.List;
import java.util.Map;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final ShopApi shopApi;
    private final OrderStockReleaser stockReleaser;
    private final PaymentApi paymentApi;
    private final ShippingApi shippingApi;

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
        order.transitionTo(OrderStatus.CANCELLED, userId, note);
        stockReleaser.release(order);
        shippingApi.cancelShipment(code, note != null ? note : "Người mua hủy đơn");
        orderRepository.flush();
        return toDetail(order);
    }

    private OrderDetail toDetail(Order order) {
        ShopSummary shop = shopApi.findSummaries(List.of(order.getShopId())).get(order.getShopId());
        return OrderDetail.of(order, shop, paymentApi.paymentTtl(),
                shippingApi.findByOrderCode(order.getCode()).orElse(null));
    }
}
