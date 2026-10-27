package com.shopvi.notification.application;

import com.shopvi.catalog.ProductModerated;
import com.shopvi.notification.domain.NotificationType;
import com.shopvi.order.OrderPaid;
import com.shopvi.order.OrderPlaced;
import com.shopvi.order.OrderStatusChanged;
import com.shopvi.shop.ShopApi;
import com.shopvi.shop.ShopStatusChanged;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Biến sự kiện nghiệp vụ thành thông báo. Chạy sau khi transaction phát sự kiện commit (bất đồng bộ, có
 * event publication registry nên không mất sự kiện khi lỗi — Modulith gửi lại khi khởi động).
 */
@Component
@RequiredArgsConstructor
class NotificationListener {

    private final NotificationService notifications;
    private final ShopApi shopApi;

    @ApplicationModuleListener
    void on(OrderStatusChanged e) {
        Map<String, Object> params = params("orderCode", e.orderCode(), "status", e.to(), "note", e.note());
        // Người mua tự thao tác (hủy, xác nhận đã nhận) thì không cần báo lại chính họ
        if (!Objects.equals(e.changedBy(), e.buyerId())) {
            notifications.notify(e.buyerId(), NotificationType.ORDER_STATUS, params, buyerLink(e.orderCode()));
        } else if ("CANCELLED".equals(e.to())) {
            toShopOwner(e.shopId(), NotificationType.ORDER_CANCELLED_BY_BUYER, params, sellerLink(e.orderCode()));
        }
    }

    @ApplicationModuleListener
    void on(OrderPlaced e) {
        // Đơn online chỉ báo seller khi đã thanh toán (OrderPaid)
        if ("COD".equals(e.paymentMethod())) {
            toShopOwner(e.shopId(), NotificationType.NEW_ORDER, params("orderCode", e.orderCode(), "total", e.total()),
                    sellerLink(e.orderCode()));
        }
    }

    @ApplicationModuleListener
    void on(OrderPaid e) {
        Map<String, Object> params = params("orderCode", e.orderCode(), "total", e.total());
        notifications.notify(e.buyerId(), NotificationType.PAYMENT_SUCCEEDED, params, buyerLink(e.orderCode()));
        toShopOwner(e.shopId(), NotificationType.NEW_ORDER, params, sellerLink(e.orderCode()));
    }

    @ApplicationModuleListener
    void on(ShopStatusChanged e) {
        NotificationType type = switch (e.status()) {
            case "REJECTED" -> NotificationType.SHOP_REJECTED;
            case "BANNED" -> NotificationType.SHOP_BANNED;
            default -> "BANNED".equals(e.from()) ? NotificationType.SHOP_UNBANNED : NotificationType.SHOP_APPROVED;
        };
        notifications.notify(e.ownerId(), type, params("shopName", e.shopName(), "reason", e.reason()),
                "/seller-center/shop");
    }

    @ApplicationModuleListener
    void on(ProductModerated e) {
        toShopOwner(e.shopId(), e.banned() ? NotificationType.PRODUCT_BANNED : NotificationType.PRODUCT_UNBANNED,
                params("productName", e.productName(), "reason", e.reason()), "/seller-center/products/" + e.productId());
    }

    private void toShopOwner(Long shopId, NotificationType type, Map<String, Object> params, String link) {
        shopApi.findOwnerId(shopId).ifPresent(owner -> notifications.notify(owner, type, params, link));
    }

    private static String buyerLink(String orderCode) {
        return "/account/orders/" + orderCode;
    }

    private static String sellerLink(String orderCode) {
        return "/seller-center/orders/" + orderCode;
    }

    /** Map tham số bỏ qua giá trị null (JSONB gọn, FE chỉ cần kiểm tra có/không). */
    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] != null) {
                map.put((String) kv[i], kv[i + 1]);
            }
        }
        return map;
    }
}
