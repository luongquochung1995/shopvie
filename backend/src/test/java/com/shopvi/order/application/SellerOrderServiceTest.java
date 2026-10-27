package com.shopvi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.OrderStatusChanged;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import com.shopvi.order.domain.OrderStatus;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.PaymentStatus;
import com.shopvi.order.domain.SellerAction;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.promotion.PromotionApi;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shipping.ShippingApi.CreateShipmentRequest;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class SellerOrderServiceTest {

    private static final Long SELLER = 5L;
    private static final Long SHOP = 3L;

    private final OrderRepository orders = mock(OrderRepository.class);
    private final ShopApi shops = mock(ShopApi.class);
    private final CatalogApi catalog = mock(CatalogApi.class);
    private final ShippingApi shipping = mock(ShippingApi.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final SellerOrderService service = new SellerOrderService(orders, shops, new OrderStockReleaser(catalog, mock(PromotionApi.class)),
            new OrderEvents(events), shipping);
    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.place("code-1", UUID.randomUUID(), 7L, SHOP, PaymentMethod.COD,
                new ShippingAddress("Nguyễn A", "0901234567", "79", null, "26740", "1 Lê Lợi"), null, 30_000, List.of(
                        new OrderItem.Snapshot(20L, 10L, "Áo", "ao-1", "Đỏ", null, 100_000, 2),
                        new OrderItem.Snapshot(15L, 11L, "Quần", "quan-2", null, null, 50_000, 1)));
        when(shops.requireActiveShopIdOfOwner(SELLER)).thenReturn(SHOP);
        when(orders.findByCodeAndShopIdForUpdate("code-1", SHOP)).thenReturn(Optional.of(order));
        when(shops.findSummaries(any())).thenReturn(Map.of(SHOP, new ShopSummary(SHOP, "Shop A", "shop-a", null, BigDecimal.ONE)));
        when(shops.findPickupAddress(SHOP)).thenReturn(Optional.of(new PickupAddress("Kho A", "0901111222", "01", "00004", "25 Kim Mã")));
        when(shipping.findByOrderCode(any())).thenReturn(Optional.empty());
    }

    @Test
    void fullFlowToDeliveredMarksCodAsPaid() {
        assertThat(service.perform(SELLER, "code-1", SellerAction.CONFIRM, null).actions())
                .containsExactly(SellerAction.SHIP, SellerAction.CANCEL);
        service.perform(SELLER, "code-1", SellerAction.SHIP, " GHN123 ");
        var delivered = service.perform(SELLER, "code-1", SellerAction.DELIVER, null);

        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(delivered.actions()).isEmpty();
        assertThat(delivered.history()).extracting("toStatus").containsExactly(OrderStatus.PENDING,
                OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.DELIVERED);
        assertThat(order.getHistory().get(2).getNote()).isEqualTo("GHN123");
        assertThat(order.getHistory().get(2).getChangedBy()).isEqualTo(SELLER);
        verify(catalog, never()).releaseStock(anyLong(), anyInt());
    }

    @Test
    void confirmCreatesShipmentFromPickupAddressWithCodAndManualStepsAreRecorded() {
        service.perform(SELLER, "code-1", SellerAction.CONFIRM, null);

        ArgumentCaptor<CreateShipmentRequest> req = ArgumentCaptor.forClass(CreateShipmentRequest.class);
        verify(shipping).createShipment(req.capture());
        assertThat(req.getValue().from().provinceCode()).isEqualTo("01");
        assertThat(req.getValue().to().wardCode()).isEqualTo("26740");
        assertThat(req.getValue().itemCount()).isEqualTo(3);
        assertThat(req.getValue().codAmount()).isEqualTo(order.getTotal());

        service.perform(SELLER, "code-1", SellerAction.SHIP, "GHN123");
        verify(shipping).recordManualStatus("code-1", ShipmentStatus.IN_TRANSIT, "GHN123");
        // Mỗi lần đổi trạng thái phát một sự kiện (người mua nhận thông báo realtime)
        ArgumentCaptor<OrderStatusChanged> changed = ArgumentCaptor.forClass(OrderStatusChanged.class);
        verify(events, times(2)).publishEvent(changed.capture());
        assertThat(changed.getAllValues()).extracting(OrderStatusChanged::to).containsExactly("CONFIRMED", "SHIPPING");
        assertThat(changed.getAllValues().get(0).changedBy()).isEqualTo(SELLER);
    }

    @Test
    void cancelRequiresReasonAndRestoresStockInVariantOrder() {
        assertThatThrownBy(() -> service.perform(SELLER, "code-1", SellerAction.CANCEL, "  "))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);

        var cancelled = service.perform(SELLER, "code-1", SellerAction.CANCEL, "Hết hàng");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        InOrder inOrder = inOrder(catalog);
        inOrder.verify(catalog).releaseStock(15L, 1);
        inOrder.verify(catalog).releaseStock(20L, 2);
        verify(shipping).cancelShipment("code-1", "Hết hàng");
    }

    @Test
    void cannotSkipSteps() {
        assertThatThrownBy(() -> service.perform(SELLER, "code-1", SellerAction.DELIVER, null))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void orderOfAnotherShopIsNotFound() {
        when(orders.findByCodeAndShopIdForUpdate("other", SHOP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.perform(SELLER, "other", SellerAction.CONFIRM, null))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void countsIncludeEveryStatus() {
        when(shops.requireShopIdOfOwner(SELLER)).thenReturn(SHOP);
        when(orders.countByStatus(SHOP)).thenReturn(List.of(count(OrderStatus.PENDING, 4)));

        var counts = service.countByStatus(SELLER);

        assertThat(counts).hasSize(OrderStatus.values().length).containsEntry(OrderStatus.PENDING, 4L)
                .containsEntry(OrderStatus.SHIPPING, 0L);
    }

    @Test
    void availableActionsFollowStateMachine() {
        assertThat(SellerAction.availableFor(OrderStatus.PENDING)).containsExactly(SellerAction.CONFIRM, SellerAction.CANCEL);
        assertThat(SellerAction.availableFor(OrderStatus.SHIPPING)).containsExactly(SellerAction.DELIVER);
        assertThat(SellerAction.availableFor(OrderStatus.DELIVERED)).isEmpty();
        assertThat(SellerAction.availableFor(OrderStatus.CANCELLED)).isEmpty();
    }

    private static OrderRepository.StatusCount count(OrderStatus status, long n) {
        return new OrderRepository.StatusCount() {
            @Override
            public OrderStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return n;
            }
        };
    }
}
