package com.shopvi.shipping.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shipping.Location;
import com.shopvi.shipping.ShipmentProgressed;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShipmentView;
import com.shopvi.shipping.ShippingApi;
import com.shopvi.shipping.config.ShippingProperties;
import com.shopvi.shipping.domain.Shipment;
import com.shopvi.shipping.infra.GhnWebhookParser;
import com.shopvi.shipping.infra.ShipmentRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Vận chuyển: GHN khi đã cấu hình, bảng phí / vận đơn nội bộ khi chưa (hoặc khi GHN lỗi lúc tính phí). */
@Slf4j
@Service
public class ShippingService implements ShippingApi {

    private final ShipmentRepository repository;
    private final ShippingProperties properties;
    private final GhnWebhookParser webhookParser;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ShippingProvider ghn;
    private final ShippingProvider internal;

    public ShippingService(ShipmentRepository repository, ShippingProperties properties, GhnWebhookParser webhookParser,
                           ApplicationEventPublisher events, Clock clock, List<ShippingProvider> providers) {
        this.repository = repository;
        this.properties = properties;
        this.webhookParser = webhookParser;
        this.events = events;
        this.clock = clock;
        Map<Shipment.Provider, ShippingProvider> byId = providers.stream()
                .collect(Collectors.toMap(ShippingProvider::id, Function.identity()));
        this.ghn = byId.get(Shipment.Provider.GHN);
        this.internal = byId.get(Shipment.Provider.INTERNAL);
    }

    @Override
    public long quote(QuoteRequest req) {
        Location origin = req.origin() != null && StringUtils.hasText(req.origin().provinceCode()) ? req.origin()
                : new Location(properties.defaultOriginProvince(), null);
        int weight = weightOf(req.itemCount());
        if (ghn != null && ghn.isEnabled()) {
            try {
                return ghn.quote(origin, req.destination(), weight, req.itemsValue());
            } catch (RuntimeException ex) {
                log.warn("GHN quote failed, falling back to internal rates: {}", ex.getMessage());
            }
        }
        return internal.quote(origin, req.destination(), weight, req.itemsValue());
    }

    @Override
    @Transactional
    public ShipmentView createShipment(CreateShipmentRequest req) {
        Optional<Shipment> existing = repository.findByOrderCode(req.orderCode());
        if (existing.isPresent()) {
            return existing.get().toView();
        }
        ShippingProvider provider = ghn != null && ghn.isEnabled() ? ghn : internal;
        ShippingProvider.Created created = provider.create(req.orderCode(), req.from(), req.to(),
                weightOf(req.itemCount()), req.itemsValue(), req.codAmount(), req.note(), req.items());
        Shipment shipment = repository.save(Shipment.create(req.orderCode(), provider.id(), created.trackingCode(),
                created.fee(), created.expectedDeliveryAt(), "Đã tạo vận đơn, chờ lấy hàng", clock.instant()));
        return shipment.toView();
    }

    @Override
    @Transactional
    public void recordManualStatus(String orderCode, ShipmentStatus status, String note) {
        repository.findByOrderCodeForUpdate(orderCode).ifPresent(s -> s.record(status, null,
                StringUtils.hasText(note) ? note : defaultDescription(status), clock.instant(), null));
    }

    @Override
    @Transactional
    public void cancelShipment(String orderCode, String reason) {
        repository.findByOrderCodeForUpdate(orderCode).ifPresent(s -> {
            if (s.isFinal()) {
                return;
            }
            if (s.getProvider() == Shipment.Provider.GHN && ghn != null && ghn.isEnabled()) {
                ghn.cancel(s.getTrackingCode());
            }
            s.record(ShipmentStatus.CANCELLED, null, reason, clock.instant(), null);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentView> findByOrderCode(String orderCode) {
        return repository.findByOrderCode(orderCode).map(Shipment::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ShipmentView> findByOrderCodes(Collection<String> orderCodes) {
        if (orderCodes.isEmpty()) {
            return Map.of();
        }
        return repository.findByOrderCodeIn(orderCodes).stream()
                .collect(Collectors.toMap(Shipment::getOrderCode, Shipment::toView));
    }

    /**
     * Webhook GHN: ghi hành trình (bỏ qua bản tin trùng) và phát {@link ShipmentProgressed} khi trạng thái đổi.
     * Ném {@code UNAUTHORIZED} nếu token webhook sai/chưa cấu hình.
     */
    @Transactional
    public void handleGhnWebhook(String token, Map<String, Object> body) {
        String expected = properties.ghn().webhookToken();
        if (!StringUtils.hasText(expected) || token == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        webhookParser.parse(body, clock.instant()).ifPresentOrElse(update ->
                repository.findByTrackingCodeForUpdate(update.trackingCode()).ifPresentOrElse(shipment -> {
                    String description = update.description() != null ? update.description()
                            : defaultDescription(update.status());
                    if (shipment.record(update.status(), update.rawStatus(), description, update.occurredAt(), body)) {
                        events.publishEvent(new ShipmentProgressed(shipment.getOrderCode(), update.status(), description));
                    }
                }, () -> log.warn("GHN webhook for unknown shipment {}", update.trackingCode())),
                () -> log.debug("Ignored GHN webhook: {}", body.get("Status")));
    }

    private int weightOf(int itemCount) {
        return Math.max(1, itemCount) * properties.itemWeightGrams();
    }

    private static String defaultDescription(ShipmentStatus status) {
        return switch (status) {
            case CREATED -> "Đã tạo vận đơn, chờ lấy hàng";
            case PICKED_UP -> "Đơn vị vận chuyển đã lấy hàng";
            case IN_TRANSIT -> "Đơn hàng đang được vận chuyển";
            case DELIVERED -> "Giao hàng thành công";
            case FAILED -> "Giao hàng không thành công";
            case RETURNED -> "Đơn hàng đang được hoàn về người bán";
            case CANCELLED -> "Đã hủy vận đơn";
        };
    }
}
