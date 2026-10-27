package com.shopvi.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.cart.CartApi;
import com.shopvi.cart.CartLine;
import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.order.application.CheckoutViews.PlaceOrderCommand;
import com.shopvi.order.config.OrderProperties;
import com.shopvi.order.domain.Order;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.payment.PaymentApi;
import com.shopvi.promotion.PromotionApi;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.PaymentLink;
import com.shopvi.payment.PaymentRequest;
import com.shopvi.shipping.Location;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shipping.ShippingApi.QuoteRequest;
import com.shopvi.shop.PickupAddress;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import com.shopvi.user.AddressSnapshot;
import com.shopvi.user.UserApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class CheckoutServiceTest {

    private static final Long USER = 7L;
    private static final String IP = "203.0.113.5";
    private static final AddressSnapshot ADDRESS = new AddressSnapshot(5L, "A", "0901234567", "79", null, "26740", "x");

    private final CartApi cart = mock(CartApi.class);
    private final CatalogApi catalog = mock(CatalogApi.class);
    private final ShopApi shops = mock(ShopApi.class);
    private final UserApi users = mock(UserApi.class);
    private final PaymentApi payments = mock(PaymentApi.class);
    private final ShippingApi shipping = mock(ShippingApi.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PromotionApi promotions = mock(PromotionApi.class);
    private final CheckoutService service = new CheckoutService(cart, catalog, shops, users, payments, promotions,
            shipping, orders, new OrderProperties(30_000), new OrderEvents(mock(ApplicationEventPublisher.class)),
            Clock.systemUTC());

    @BeforeEach
    void setUp() {
        // 2 shop: shop 1 có 2 món, shop 2 có 1 món
        when(cart.selectedLines(USER)).thenReturn(List.of(
                new CartLine(1L, 101L, 2), new CartLine(2L, 102L, 1), new CartLine(3L, 201L, 3)));
        when(catalog.findVariants(any())).thenReturn(Map.of(
                101L, variant(101L, 1L, 100_000, 10),
                102L, variant(102L, 1L, 50_000, 10),
                201L, variant(201L, 2L, 20_000, 10)));
        when(shops.findSummaries(any())).thenReturn(Map.of(
                1L, new ShopSummary(1L, "S1", "s1", null, BigDecimal.ONE),
                2L, new ShopSummary(2L, "S2", "s2", null, BigDecimal.ONE)));
        when(users.findDefaultAddress(USER)).thenReturn(Optional.of(ADDRESS));
        when(users.findAddress(USER, 5L)).thenReturn(Optional.of(ADDRESS));
        when(orders.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-10-26T10:00:00Z"));
            return order;
        });
        when(shipping.quote(any())).thenReturn(30_000L);
        when(payments.availableChannels()).thenReturn(Set.of());
        when(payments.paymentTtl()).thenReturn(Duration.ofMinutes(15));
    }

    @Test
    void previewGroupsByShopWithShippingPerShop() {
        var preview = service.preview(USER, null, null);

        assertThat(preview.groups()).hasSize(2);
        assertThat(preview.groups().get(0).subtotal()).isEqualTo(250_000);
        assertThat(preview.groups().get(1).subtotal()).isEqualTo(60_000);
        assertThat(preview.shippingFee()).isEqualTo(60_000);
        assertThat(preview.total()).isEqualTo(370_000);
        assertThat(preview.address()).isEqualTo(ADDRESS);
        assertThat(preview.paymentMethods()).containsExactly("COD");
    }

    @Test
    void placeOrderSplitsByShopReservesStockInVariantOrderAndClearsCart() {
        var result = service.placeOrder(USER, new PlaceOrderCommand(5L, "COD", Map.of(2L, "  gọi trước  "), 370_000L, null), IP);

        assertThat(result.orders()).hasSize(2);
        assertThat(result.total()).isEqualTo(370_000);
        InOrder stock = inOrder(catalog);
        stock.verify(catalog).reserveStock(101L, 2);
        stock.verify(catalog).reserveStock(102L, 1);
        stock.verify(catalog).reserveStock(201L, 3);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Order::getTotal).containsExactly(280_000L, 90_000L);
        assertThat(saved.getAllValues().get(1).getNote()).isEqualTo("gọi trước");
        assertThat(saved.getAllValues().get(0).getCheckoutId()).isEqualTo(saved.getAllValues().get(1).getCheckoutId());
        verify(cart).removeVariants(USER, List.of(101L, 102L, 201L));
    }

    @Test
    void priceChangedSinceReviewIsRejectedBeforeTouchingStock() {
        assertThatThrownBy(() -> service.placeOrder(USER, new PlaceOrderCommand(5L, "COD", null, 1L, null), IP))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PRICE_CHANGED);
        verify(catalog, never()).reserveStock(anyLong(), anyInt());
    }

    @Test
    void outOfStockDuringReservationPropagatesSoTransactionRollsBack() {
        doThrow(new BusinessException(ErrorCode.OUT_OF_STOCK)).when(catalog).reserveStock(102L, 1);

        assertThatThrownBy(() -> service.placeOrder(USER, new PlaceOrderCommand(5L, "COD", null, null, null), IP))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
        verify(orders, never()).save(any());
        verify(cart, never()).removeVariants(any(), any());
    }

    @Test
    void itemsNotEnoughStockInCartAreRejected() {
        when(catalog.findVariants(any())).thenReturn(Map.of(
                101L, variant(101L, 1L, 100_000, 1),
                102L, variant(102L, 1L, 50_000, 10),
                201L, variant(201L, 2L, 20_000, 10)));

        assertThat(service.preview(USER, null, null).hasUnavailableItems()).isTrue();
        assertThatThrownBy(() -> service.placeOrder(USER, new PlaceOrderCommand(5L, "COD", null, null, null), IP))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void onlineMethodRequiresConfiguredGateway() {
        assertThatThrownBy(() -> service.placeOrder(USER, new PlaceOrderCommand(5L, "VNPAY", null, null, null), IP))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
    }

    @Test
    void emptySelectionIsRejected() {
        when(cart.selectedLines(USER)).thenReturn(List.of());

        assertThatThrownBy(() -> service.preview(USER, null, null))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NO_ITEMS_SELECTED);
    }

    @Test
    void addressOfAnotherUserIsNotFound() {
        when(users.findAddress(USER, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(USER, new PlaceOrderCommand(99L, "COD", null, null, null), IP))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void shippingIsQuotedFromShopPickupAddressToReceiver() {
        when(shops.findPickupAddress(1L)).thenReturn(Optional.of(new PickupAddress("S1", "0901", "01", "00004", "x")));
        when(shops.findPickupAddress(2L)).thenReturn(Optional.empty());

        service.preview(USER, null, null);

        ArgumentCaptor<QuoteRequest> quotes = ArgumentCaptor.forClass(QuoteRequest.class);
        verify(shipping, org.mockito.Mockito.times(2)).quote(quotes.capture());
        assertThat(quotes.getAllValues().get(0)).isEqualTo(new QuoteRequest(new Location("01", "00004"),
                new Location("79", "26740"), 3, 250_000));
        // Shop chưa khai báo địa chỉ lấy hàng → origin null (module shipping dùng tỉnh mặc định)
        assertThat(quotes.getAllValues().get(1).origin()).isNull();
    }

    @Test
    void previewWithoutAddressUsesEstimatedFee() {
        when(users.findDefaultAddress(USER)).thenReturn(Optional.empty());

        var preview = service.preview(USER, null, null);

        assertThat(preview.shippingFee()).isEqualTo(60_000);
        verify(shipping, never()).quote(any());
    }

    @Test
    void onlinePaymentReturnsGatewayUrlForWholeCheckout() {
        when(payments.availableChannels()).thenReturn(Set.of(PaymentChannel.VNPAY));
        when(payments.initiate(any())).thenReturn(new PaymentLink("https://pay.example/x", Instant.parse("2026-10-26T10:15:00Z")));

        var preview = service.preview(USER, null, null);
        var result = service.placeOrder(USER, new PlaceOrderCommand(5L, "VNPAY", null, null, null), IP);

        assertThat(preview.paymentMethods()).containsExactly("COD", "VNPAY");
        assertThat(result.paymentUrl()).isEqualTo("https://pay.example/x");
        ArgumentCaptor<PaymentRequest> req = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(payments).initiate(req.capture());
        assertThat(req.getValue().amount()).isEqualTo(370_000);
        assertThat(req.getValue().channel()).isEqualTo(PaymentChannel.VNPAY);
        assertThat(req.getValue().clientIp()).isEqualTo(IP);
        assertThat(req.getValue().checkoutId()).isEqualTo(result.checkoutId());
    }

    @Test
    void codDoesNotCreatePayment() {
        var result = service.placeOrder(USER, new PlaceOrderCommand(5L, "COD", null, null, null), IP);

        assertThat(result.paymentUrl()).isNull();
        verify(payments, never()).initiate(any());
    }

    private static VariantSnapshot variant(Long id, Long shopId, long price, int stock) {
        return new VariantSnapshot(id, "SKU" + id, id * 10, "P" + id, "p-" + id, shopId, null, null, price, stock, true);
    }
}
