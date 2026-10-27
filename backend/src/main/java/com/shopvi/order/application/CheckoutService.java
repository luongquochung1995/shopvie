package com.shopvi.order.application;

import com.shopvi.cart.CartApi;
import com.shopvi.cart.CartLine;
import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.common.util.Uuid7;
import com.shopvi.order.application.CheckoutViews.CheckoutPayment;
import com.shopvi.order.application.CheckoutViews.CheckoutPaymentStatus;
import com.shopvi.order.application.CheckoutViews.Line;
import com.shopvi.order.application.CheckoutViews.PlaceOrderCommand;
import com.shopvi.order.application.CheckoutViews.PlaceOrderResult;
import com.shopvi.order.application.CheckoutViews.PlacedOrder;
import com.shopvi.order.application.CheckoutViews.Preview;
import com.shopvi.order.application.CheckoutViews.ShopGroup;
import com.shopvi.order.config.OrderProperties;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.PaymentStatus;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.order.domain.OrderVoucher;
import com.shopvi.payment.PaymentApi;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.PaymentLink;
import com.shopvi.payment.PaymentRequest;
import com.shopvi.payment.PaymentState;
import com.shopvi.promotion.PromotionApi;
import com.shopvi.promotion.PromotionApi.AppliedVoucher;
import com.shopvi.promotion.PromotionApi.Redemption;
import com.shopvi.promotion.PromotionApi.ShopAmount;
import com.shopvi.promotion.PromotionApi.VoucherQuote;
import com.shopvi.shipping.Location;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shipping.ShippingApi.QuoteRequest;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopSummary;
import com.shopvi.user.AddressSnapshot;
import com.shopvi.user.UserApi;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final int MAX_NOTE_LENGTH = 500;

    private final CartApi cartApi;
    private final CatalogApi catalogApi;
    private final ShopApi shopApi;
    private final UserApi userApi;
    private final PaymentApi paymentApi;
    private final PromotionApi promotionApi;
    private final ShippingApi shippingApi;
    private final OrderRepository orderRepository;
    private final OrderProperties properties;
    private final OrderEvents orderEvents;
    private final Clock clock;

    /** Xem trước: tính tiền các món đã chọn, tách theo shop, phí ship theo địa chỉ nhận. */
    @Transactional(readOnly = true)
    public Preview preview(Long userId, Long addressId, List<String> voucherCodes) {
        AddressSnapshot address = addressId != null
                ? userApi.findAddress(userId, addressId).orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND))
                : userApi.findDefaultAddress(userId).orElse(null);
        return quote(userId, address, voucherCodes);
    }

    /**
     * Đặt hàng: tách đơn theo shop, trừ kho nguyên tử, xóa món đã mua khỏi giỏ — tất cả trong một transaction;
     * bất kỳ bước nào lỗi thì không có gì được ghi. Thanh toán online: tạo giao dịch và trả URL cổng thanh toán
     * (đơn chờ thanh toán, quá hạn sẽ bị hủy và hoàn kho).
     */
    @Transactional
    public PlaceOrderResult placeOrder(Long userId, PlaceOrderCommand cmd, String clientIp) {
        PaymentMethod method = parsePayment(cmd.paymentMethod());
        if (cmd.addressId() == null) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        AddressSnapshot address = userApi.findAddress(userId, cmd.addressId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        Preview quote = quote(userId, address, cmd.voucherCodes());

        List<ErrorDetail> unavailable = quote.groups().stream().flatMap(g -> g.items().stream())
                .filter(l -> !l.available())
                .map(l -> new ErrorDetail("variantId", String.valueOf(l.variantId())))
                .toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, ErrorCode.OUT_OF_STOCK.getDefaultMessage(), unavailable);
        }
        if (cmd.expectedTotal() != null && cmd.expectedTotal() != quote.total()) {
            throw new BusinessException(ErrorCode.PRICE_CHANGED);
        }

        // Trừ kho theo thứ tự variantId để các checkout đồng thời khóa dòng cùng thứ tự (tránh deadlock)
        quote.groups().stream().flatMap(g -> g.items().stream())
                .sorted(Comparator.comparing(Line::variantId))
                .forEach(l -> catalogApi.reserveStock(l.variantId(), l.quantity()));

        UUID checkoutId = Uuid7.next(clock);
        Map<Long, String> notes = cmd.notes() == null ? Map.of() : cmd.notes();
        List<Order> created = new ArrayList<>();
        List<Redemption> redemptions = new ArrayList<>();
        List<PlacedOrder> placed = quote.groups().stream().map(g -> {
            List<OrderItem.Snapshot> lines = g.items().stream()
                    .map(l -> new OrderItem.Snapshot(l.variantId(), l.productId(), l.productName(), l.productSlug(),
                            l.variantName(), l.imageUrl(), l.unitPrice(), l.quantity()))
                    .toList();
            Order order = orderRepository.save(Order.place(Uuid7.next(clock).toString(), checkoutId, userId,
                    g.shop().id(), method, ShippingAddress.of(address), note(notes.get(g.shop().id())),
                    g.shippingFee(), g.vouchers().stream()
                            .map(v -> new OrderVoucher(v.code(), v.type(), v.scope(), v.amount())).toList(), lines));
            created.add(order);
            orderEvents.placed(order);
            g.vouchers().forEach(v -> redemptions.add(new Redemption(v.voucherId(), order.getId(), v.amount())));
            return new PlacedOrder(order.getCode(), order.getShopId(), order.getTotal());
        }).toList();

        // Ghi lượt dùng voucher (nguyên tử theo giới hạn lượt) — lỗi thì rollback cả lần đặt hàng
        if (!redemptions.isEmpty()) {
            promotionApi.redeemVouchers(userId, checkoutId, redemptions);
        }
        cartApi.removeVariants(userId, quote.groups().stream()
                .flatMap(g -> g.items().stream()).map(Line::variantId).toList());

        PaymentLink link = method == PaymentMethod.COD ? null : paymentApi.initiate(new PaymentRequest(checkoutId,
                userId, PaymentChannel.valueOf(method.name()), quote.total(), clientIp, expiresAt(created)));
        return new PlaceOrderResult(checkoutId, placed, quote.total(), link == null ? null : link.paymentUrl(),
                link == null ? null : link.expiresAt());
    }

    /** Thanh toán lại (giao dịch trước thất bại / người mua thoát cổng) trong thời hạn thanh toán của checkout. */
    @Transactional
    public PaymentLink retryPayment(Long userId, UUID checkoutId, String clientIp) {
        List<Order> orders = ordersOf(userId, checkoutId);
        List<Order> awaiting = orders.stream().filter(Order::isAwaitingPayment).toList();
        if (awaiting.isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_REQUIRED);
        }
        Instant expiresAt = expiresAt(orders);
        if (!clock.instant().isBefore(expiresAt)) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        long amount = awaiting.stream().mapToLong(Order::getTotal).sum();
        return paymentApi.initiate(new PaymentRequest(checkoutId, userId,
                PaymentChannel.valueOf(awaiting.get(0).getPaymentMethod().name()), amount, clientIp, expiresAt));
    }

    /** Trạng thái thanh toán của checkout — trang kết quả thanh toán hỏi lại cho tới khi có kết quả cuối. */
    @Transactional(readOnly = true)
    public CheckoutPayment paymentStatus(Long userId, UUID checkoutId) {
        List<Order> orders = ordersOf(userId, checkoutId);
        PaymentMethod method = orders.get(0).getPaymentMethod();
        CheckoutPaymentStatus status;
        if (method == PaymentMethod.COD) {
            status = CheckoutPaymentStatus.NOT_REQUIRED;
        } else if (orders.stream().anyMatch(Order::isAwaitingPayment)) {
            status = paymentApi.latestState(checkoutId).filter(s -> s == PaymentState.FAILED).isPresent()
                    ? CheckoutPaymentStatus.FAILED : CheckoutPaymentStatus.PENDING;
        } else if (orders.stream().anyMatch(o -> o.getPaymentStatus() != PaymentStatus.UNPAID)) {
            status = CheckoutPaymentStatus.PAID;
        } else {
            status = CheckoutPaymentStatus.CANCELLED;
        }
        return new CheckoutPayment(checkoutId, method, status, orders.stream().mapToLong(Order::getTotal).sum(),
                status == CheckoutPaymentStatus.PENDING || status == CheckoutPaymentStatus.FAILED ? expiresAt(orders) : null,
                orders.stream().map(Order::getCode).toList());
    }

    /** Phương thức thanh toán dùng được: COD + các cổng online đã cấu hình. */
    List<PaymentMethod> supportedPayments() {
        Set<PaymentChannel> channels = paymentApi.availableChannels();
        List<PaymentMethod> methods = new ArrayList<>(List.of(PaymentMethod.COD));
        for (PaymentChannel c : PaymentChannel.values()) {
            if (channels.contains(c)) {
                methods.add(PaymentMethod.valueOf(c.name()));
            }
        }
        return methods;
    }

    private List<Order> ordersOf(Long userId, UUID checkoutId) {
        List<Order> orders = orderRepository.findByCheckoutIdOrderByIdAsc(checkoutId).stream()
                .filter(o -> o.getUserId().equals(userId))
                .toList();
        if (orders.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return orders;
    }

    /** Hạn thanh toán của checkout = lúc đặt (độ chính xác micro giây như DB) + thời hạn thanh toán. */
    private Instant expiresAt(List<Order> orders) {
        return orders.stream().map(Order::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow()
                .truncatedTo(ChronoUnit.MICROS).plus(paymentApi.paymentTtl());
    }

    private Preview quote(Long userId, AddressSnapshot address, List<String> voucherCodes) {
        List<CartLine> lines = cartApi.selectedLines(userId);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ITEMS_SELECTED);
        }
        Map<Long, VariantSnapshot> variants = catalogApi.findVariants(lines.stream().map(CartLine::variantId).toList());
        // SKU đã bị xóa hẳn: bỏ qua (không còn trong catalog)
        Map<Long, List<Line>> byShop = new TreeMap<>(lines.stream()
                .filter(l -> variants.containsKey(l.variantId()))
                .map(l -> toLine(variants.get(l.variantId()), l.quantity()))
                .collect(Collectors.groupingBy(l -> variants.get(l.variantId()).shopId())));
        if (byShop.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ITEMS_SELECTED);
        }
        Map<Long, ShopSummary> shops = shopApi.findSummaries(byShop.keySet());

        List<ShopAmount> amounts = byShop.entrySet().stream().map(e -> {
            long subtotal = e.getValue().stream().mapToLong(Line::lineTotal).sum();
            int itemCount = e.getValue().stream().mapToInt(Line::quantity).sum();
            return new ShopAmount(e.getKey(), subtotal, shippingFee(e.getKey(), address, itemCount, subtotal));
        }).toList();
        VoucherQuote vouchers = voucherCodes == null || voucherCodes.isEmpty() ? VoucherQuote.empty()
                : promotionApi.quoteVouchers(userId, voucherCodes, amounts);
        List<ShopGroup> groups = amounts.stream().map(a -> {
            long discount = vouchers.discountOf(a.shopId());
            return new ShopGroup(shops.get(a.shopId()), byShop.get(a.shopId()), a.subtotal(), a.shippingFee(), discount,
                    a.subtotal() + a.shippingFee() - discount, vouchers.byShop().getOrDefault(a.shopId(), List.of()));
        }).toList();
        long subtotal = groups.stream().mapToLong(ShopGroup::subtotal).sum();
        long shipping = groups.stream().mapToLong(ShopGroup::shippingFee).sum();
        long discount = groups.stream().mapToLong(ShopGroup::discount).sum();
        boolean hasUnavailable = groups.stream().flatMap(g -> g.items().stream()).anyMatch(l -> !l.available());
        List<String> applied = vouchers.byShop().values().stream().flatMap(List::stream)
                .map(AppliedVoucher::code).distinct().toList();
        return new Preview(address, groups, subtotal, shipping, discount, subtotal + shipping - discount,
                supportedPayments().stream().map(Enum::name).toList(), hasUnavailable, applied);
    }

    /** Phí ship từ địa chỉ lấy hàng của shop tới địa chỉ nhận; chưa có địa chỉ nhận thì dùng phí ước tính. */
    long shippingFee(Long shopId, AddressSnapshot address, int itemCount, long subtotal) {
        if (address == null) {
            return properties.shippingFee();
        }
        Location origin = shopApi.findPickupAddress(shopId)
                .map(p -> new Location(p.provinceCode(), p.wardCode()))
                .orElse(null);
        return shippingApi.quote(new QuoteRequest(origin, new Location(address.provinceCode(), address.wardCode()),
                itemCount, subtotal));
    }

    private static Line toLine(VariantSnapshot v, int quantity) {
        boolean available = v.purchasable() && v.stock() >= quantity;
        return new Line(v.variantId(), v.productId(), v.productName(), v.productSlug(), v.variantName(), v.imageUrl(),
                v.price(), quantity, v.stock(), available, v.price() * quantity);
    }

    private PaymentMethod parsePayment(String value) {
        return supportedPayments().stream().filter(m -> m.name().equals(value)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED));
    }

    private static String note(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > MAX_NOTE_LENGTH ? trimmed.substring(0, MAX_NOTE_LENGTH) : trimmed;
    }
}
