package com.shopvi.order.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.order.application.SellerOrderViews.SellerOrderDetail;
import com.shopvi.order.application.SellerOrderViews.SellerOrderSummary;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.SellerAction;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.shipping.Party;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shipping.ShippingApi.CreateShipmentRequest;
import com.shopvi.shipping.ShippingApi.Item;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Seller xử lý đơn của shop (P3-3): xác nhận (tạo vận đơn) → giao cho vận chuyển → đã giao, hoặc hủy (hoàn kho).
 * Đơn của shop khác coi như không tồn tại (404 ORDER_NOT_FOUND).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerOrderService {

    private final OrderRepository orderRepository;
    private final ShopApi shopApi;
    private final OrderStockReleaser stockReleaser;
    private final OrderEvents orderEvents;
    private final ShippingApi shippingApi;

    public Page<SellerOrderSummary> list(Long sellerId, OrderStatus status, Pageable pageable) {
        Long shopId = shopApi.requireShopIdOfOwner(sellerId);
        Page<Order> page = status == null
                ? orderRepository.findByShopId(shopId, pageable)
                : orderRepository.findByShopIdAndStatus(shopId, status, pageable);
        return page.map(SellerOrderSummary::of);
    }

    /** Số đơn theo từng trạng thái (đủ mọi trạng thái, không có đơn = 0) cho các tab và dashboard. */
    public Map<OrderStatus, Long> countByStatus(Long sellerId) {
        return countByStatusOfShop(shopApi.requireShopIdOfOwner(sellerId));
    }

    Map<OrderStatus, Long> countByStatusOfShop(Long shopId) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        orderRepository.countByStatus(shopId).forEach(c -> counts.put(c.getStatus(), c.getCount()));
        return counts;
    }

    public SellerOrderDetail detail(Long sellerId, String code) {
        Long shopId = shopApi.requireShopIdOfOwner(sellerId);
        Order order = orderRepository.findByCodeAndShopId(code, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return SellerOrderDetail.of(order, shippingApi.findByOrderCode(code).orElse(null));
    }

    /**
     * Thực hiện thao tác của seller; trạng thái không cho phép → 409 INVALID_ORDER_STATUS_TRANSITION.
     * Xác nhận đơn tạo vận đơn; giao vận chuyển / đã giao ghi vào hành trình; hủy (bắt buộc lý do) hoàn kho
     * và hủy vận đơn — tất cả trong cùng transaction.
     */
    @Transactional
    public SellerOrderDetail perform(Long sellerId, String code, SellerAction action, String note) {
        Long shopId = shopApi.requireActiveShopIdOfOwner(sellerId);
        String trimmed = StringUtils.hasText(note) ? note.trim() : null;
        if (action == SellerAction.CANCEL && trimmed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                    List.of(new ErrorDetail("reason", "must not be blank")));
        }
        Order order = orderRepository.findByCodeAndShopIdForUpdate(code, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        orderEvents.transition(order, action.target(), sellerId, trimmed);
        switch (action) {
            case CONFIRM -> shippingApi.createShipment(shipmentRequest(order, shopId));
            case SHIP -> shippingApi.recordManualStatus(code, ShipmentStatus.IN_TRANSIT, trimmed);
            case DELIVER -> shippingApi.recordManualStatus(code, ShipmentStatus.DELIVERED, trimmed);
            case CANCEL -> {
                stockReleaser.release(order);
                shippingApi.cancelShipment(code, trimmed);
            }
        }
        orderRepository.flush();
        return SellerOrderDetail.of(order, shippingApi.findByOrderCode(code).orElse(null));
    }

    private CreateShipmentRequest shipmentRequest(Order order, Long shopId) {
        ShippingAddress to = order.getShippingAddress();
        ShopSummary shop = shopApi.findSummaries(List.of(shopId)).get(shopId);
        PickupAddress pickup = shopApi.findPickupAddress(shopId)
                .orElse(new PickupAddress(shop == null ? null : shop.name(), null, null, null, null));
        int itemCount = order.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        return new CreateShipmentRequest(order.getCode(),
                new Party(pickup.name(), pickup.phone(), pickup.provinceCode(), pickup.wardCode(), pickup.detail()),
                new Party(to.receiverName(), to.phone(), to.provinceCode(), to.wardCode(), to.detail()),
                itemCount, order.getSubtotal(),
                // COD: shipper thu hộ toàn bộ tiền đơn; đã thanh toán online thì không thu
                order.getPaymentMethod() == PaymentMethod.COD ? order.getTotal() : 0,
                order.getNote(),
                order.getItems().stream().map(i -> new Item(i.getProductName(), i.getQuantity())).toList());
    }
}
