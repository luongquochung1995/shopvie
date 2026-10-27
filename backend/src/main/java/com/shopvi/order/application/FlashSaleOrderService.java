package com.shopvi.order.application;

import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.util.Uuid7;
import com.shopvi.order.domain.Order;
import com.shopvi.order.domain.OrderItem;
import com.shopvi.order.domain.PaymentMethod;
import com.shopvi.order.domain.ShippingAddress;
import com.shopvi.order.infra.OrderRepository;
import com.shopvi.promotion.FlashSaleApi;
import com.shopvi.promotion.FlashSaleOrderRequested;
import com.shopvi.user.AddressSnapshot;
import com.shopvi.user.UserApi;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo đơn COD cho một lượt giữ suất flash sale: giá sale, trừ kho thật, ghi CONFIRMED bên promotion — cùng một
 * transaction. Id lượt giữ làm checkout id nên bản tin bị giao lặp không tạo đơn thứ hai.
 */
@Service
@RequiredArgsConstructor
class FlashSaleOrderService {

    static final String NOTE = "Flash sale";

    private final CatalogApi catalogApi;
    private final UserApi userApi;
    private final FlashSaleApi flashSaleApi;
    private final CheckoutService checkoutService;
    private final OrderRepository orderRepository;
    private final OrderEvents orderEvents;
    private final Clock clock;

    /** @return mã đơn đã tạo (hoặc đã có từ lần xử lý trước) */
    @Transactional
    public String create(FlashSaleOrderRequested req) {
        List<Order> existing = orderRepository.findByCheckoutIdOrderByIdAsc(req.reservationId());
        if (!existing.isEmpty()) {
            return existing.getFirst().getCode();
        }
        AddressSnapshot address = userApi.findAddress(req.userId(), req.addressId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        VariantSnapshot v = catalogApi.findVariants(List.of(req.variantId())).get(req.variantId());
        if (v == null || !v.purchasable()) {
            throw new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE);
        }
        catalogApi.reserveStock(v.variantId(), req.quantity());

        long subtotal = req.salePrice() * req.quantity();
        long shippingFee = checkoutService.shippingFee(v.shopId(), address, req.quantity(), subtotal);
        OrderItem.Snapshot line = new OrderItem.Snapshot(v.variantId(), v.productId(), v.productName(),
                v.productSlug(), v.variantName(), v.imageUrl(), req.salePrice(), req.quantity());
        Order order = orderRepository.save(Order.place(Uuid7.next(clock).toString(), req.reservationId(),
                req.userId(), v.shopId(), PaymentMethod.COD, ShippingAddress.of(address), NOTE, shippingFee,
                List.of(line)));
        flashSaleApi.confirmReservation(req, order.getCode());
        orderEvents.placed(order);
        return order.getCode();
    }
}
