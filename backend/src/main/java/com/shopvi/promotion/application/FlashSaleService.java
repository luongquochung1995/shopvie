package com.shopvi.promotion.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.catalog.CatalogApi;
import com.shopvi.catalog.VariantSnapshot;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.exception.ErrorDetail;
import com.shopvi.common.util.Uuid7;
import com.shopvi.promotion.FlashSaleApi;
import com.shopvi.promotion.FlashSaleOrderRequested;
import com.shopvi.promotion.domain.FlashSale;
import com.shopvi.promotion.domain.FlashSaleItem;
import com.shopvi.promotion.domain.FlashSaleReservation;
import com.shopvi.promotion.infra.FlashSaleItemRepository;
import com.shopvi.promotion.infra.FlashSaleRepository;
import com.shopvi.promotion.infra.FlashSaleReservationRepository;
import com.shopvi.promotion.infra.FlashSaleStock;
import com.shopvi.user.AddressSnapshot;
import com.shopvi.user.UserApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Flash sale: admin tạo phiên; người mua giữ suất trên Redis (Lua, nguyên tử) rồi đơn được tạo bất đồng bộ qua Kafka
 * (order module tiêu thụ {@link FlashSaleOrderRequested}). Client poll trạng thái lượt giữ tới khi CONFIRMED/FAILED.
 * Mọi lượt giữ kết thúc bằng đúng một dòng {@code flash_sale_reservations}: CONFIRMED (đã có đơn) hoặc FAILED (đã trả
 * suất về Redis) — khóa chính chặn hai kết cục cùng xảy ra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleService implements FlashSaleApi {

    static final int MAX_REQUESTS_PER_SECOND = 5;
    static final Duration RESERVATION_TTL = Duration.ofDays(1);
    static final long PUBLISH_TIMEOUT_MS = 5_000;

    public enum ReservationStatus { PENDING, CONFIRMED, FAILED }

    private final FlashSaleRepository saleRepository;
    private final FlashSaleItemRepository itemRepository;
    private final FlashSaleReservationRepository reservationRepository;
    private final FlashSaleStock stock;
    private final CatalogApi catalogApi;
    private final UserApi userApi;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** variantId + giá sale của item không đổi sau khi tạo → cache trong JVM để đường mua không chạm DB. */
    private final Map<Long, ItemInfo> itemInfos = new ConcurrentHashMap<>();

    // ----- Admin

    @Transactional
    public FlashSaleView create(FlashSaleCommand cmd) {
        List<ErrorDetail> errors = new ArrayList<>();
        if (!cmd.endAt().isAfter(cmd.startAt())) {
            errors.add(new ErrorDetail("endAt", "must be after startAt"));
        }
        Map<Long, VariantSnapshot> variants = catalogApi.findVariants(
                cmd.items().stream().map(ItemCommand::variantId).toList());
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < cmd.items().size(); i++) {
            ItemCommand item = cmd.items().get(i);
            VariantSnapshot v = variants.get(item.variantId());
            if (!seen.add(item.variantId())) {
                errors.add(new ErrorDetail("items[" + i + "].variantId", "duplicate"));
            } else if (v == null || !v.purchasable()) {
                errors.add(new ErrorDetail("items[" + i + "].variantId", "not found or not purchasable"));
            } else if (item.salePrice() >= v.price()) {
                errors.add(new ErrorDetail("items[" + i + "].salePrice", "must be lower than " + v.price()));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(), errors);
        }
        FlashSale sale = FlashSale.create(cmd.name().trim(), cmd.startAt(), cmd.endAt());
        cmd.items().forEach(i -> sale.addItem(i.variantId(), variants.get(i.variantId()).productId(), i.salePrice(),
                i.quota(), i.perUserLimit()));
        saleRepository.save(sale);
        afterCommit(() -> sale.getItems().forEach(this::warmUpQuietly));
        return toView(sale, variants);
    }

    @Transactional(readOnly = true)
    public Page<FlashSaleView> list(Pageable pageable) {
        Page<FlashSale> page = saleRepository.findAllByOrderByStartAtDesc(pageable);
        Map<Long, VariantSnapshot> variants = variantsOf(page.getContent());
        return page.map(s -> toView(s, variants));
    }

    @Transactional
    public FlashSaleView cancel(Long id) {
        FlashSale sale = saleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_NOT_FOUND));
        sale.cancel();
        afterCommit(() -> sale.getItems().forEach(i -> stock.unload(i.getId())));
        return toView(sale, variantsOf(List.of(sale)));
    }

    // ----- Người mua

    /** Phiên đang chạy và sắp diễn ra (tối đa 3), item kèm số suất còn lại. */
    @Transactional(readOnly = true)
    public List<FlashSaleView> current() {
        List<FlashSale> sales = saleRepository.findCurrentAndUpcoming(clock.instant()).stream().limit(3).toList();
        Map<Long, VariantSnapshot> variants = variantsOf(sales);
        return sales.stream().map(s -> toView(s, variants)).toList();
    }

    /**
     * Giữ suất và gửi yêu cầu tạo đơn (COD, giao tới địa chỉ đã chọn hoặc mặc định). Trả về id lượt giữ để poll.
     * Gửi Kafka đồng bộ; gửi lỗi thì lượt giữ được đánh FAILED (trả suất) và trả 503.
     */
    public PurchaseResult purchase(Long userId, Long itemId, int quantity, Long addressId) {
        if (!stock.tryAcquire(userId, MAX_REQUESTS_PER_SECOND, Duration.ofSeconds(1))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        AddressSnapshot address = (addressId != null ? userApi.findAddress(userId, addressId)
                : userApi.findDefaultAddress(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
        ItemInfo info = itemInfo(itemId);

        Instant now = clock.instant();
        long result = stock.reserve(itemId, userId, quantity, now);
        if (result == FlashSaleStock.NOT_LOADED) {
            warmUp(itemId);
            result = stock.reserve(itemId, userId, quantity, now);
        }
        if (result < 0) {
            throw new BusinessException(result == FlashSaleStock.SOLD_OUT ? ErrorCode.FLASH_SALE_SOLD_OUT
                    : result == FlashSaleStock.LIMIT_REACHED ? ErrorCode.FLASH_SALE_LIMIT_REACHED
                    : ErrorCode.FLASH_SALE_NOT_ACTIVE);
        }

        UUID reservationId = Uuid7.next(clock);
        FlashSaleOrderRequested request = new FlashSaleOrderRequested(reservationId, itemId, info.variantId(), userId,
                quantity, info.salePrice(), address.id());
        putState(reservationId, new ReservationState(userId, itemId, ReservationStatus.PENDING, null, null));
        try {
            kafkaTemplate.send(MessageBuilder.withPayload(request)
                            .setHeader(KafkaHeaders.TOPIC, FlashSaleOrderRequested.TOPIC)
                            .setHeader(KafkaHeaders.KEY, reservationId.toString()).build())
                    .get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return publishFailed(request, ex);
        } catch (Exception ex) {
            return publishFailed(request, ex);
        }
        return new PurchaseResult(reservationId, ReservationStatus.PENDING);
    }

    /** Trạng thái lượt giữ của chính người mua (Redis, rơi về DB khi Redis đã hết hạn key). */
    @Transactional(readOnly = true)
    public ReservationView reservation(Long userId, UUID id) {
        ReservationState state = readState(id);
        if (state == null) {
            state = reservationRepository.findById(id)
                    .map(r -> new ReservationState(r.getUserId(), r.getItemId(),
                            ReservationStatus.valueOf(r.getStatus().name()), r.getOrderCode(), r.getFailureReason()))
                    .orElse(null);
        }
        if (state == null || !state.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.FLASH_SALE_RESERVATION_NOT_FOUND);
        }
        return new ReservationView(id, state.itemId(), state.status(), state.orderCode(), state.reason());
    }

    // ----- FlashSaleApi (order module gọi khi tiêu thụ yêu cầu tạo đơn)

    @Override
    @Transactional(readOnly = true)
    public boolean isProcessed(UUID reservationId) {
        return reservationRepository.existsById(reservationId);
    }

    /** Chạy trong transaction tạo đơn: cộng số đã bán + ghi CONFIRMED; lỗi thì đơn rollback theo. */
    @Override
    @Transactional
    public void confirmReservation(FlashSaleOrderRequested req, String orderCode) {
        if (itemRepository.addSold(req.itemId(), req.quantity()) == 0) {
            throw new BusinessException(ErrorCode.FLASH_SALE_SOLD_OUT);
        }
        reservationRepository.saveAndFlush(FlashSaleReservation.confirmed(req.reservationId(), req.itemId(),
                req.userId(), req.quantity(), orderCode));
        afterCommit(() -> putState(req.reservationId(),
                new ReservationState(req.userId(), req.itemId(), ReservationStatus.CONFIRMED, orderCode, null)));
    }

    /**
     * Ghi FAILED và trả suất về Redis. Đã có kết cục (CONFIRMED/FAILED) thì không làm gì. Gọi ngoài transaction (sau khi
     * transaction tạo đơn đã rollback): lần ghi chạy trong transaction riêng của repository nên va chạm khóa chính
     * không làm hỏng transaction của người gọi.
     */
    @Override
    public void failReservation(FlashSaleOrderRequested req, String reason) {
        if (reservationRepository.existsById(req.reservationId())) {
            return;
        }
        try {
            reservationRepository.saveAndFlush(FlashSaleReservation.failed(req.reservationId(), req.itemId(),
                    req.userId(), req.quantity(), reason));
        } catch (DataIntegrityViolationException ex) {
            return; // đơn vừa được tạo song song — giữ kết cục CONFIRMED
        }
        afterCommit(() -> {
            stock.release(req.itemId(), req.userId(), req.quantity());
            putState(req.reservationId(),
                    new ReservationState(req.userId(), req.itemId(), ReservationStatus.FAILED, null, reason));
        });
    }

    // ----- Nội bộ

    private PurchaseResult publishFailed(FlashSaleOrderRequested request, Exception ex) {
        log.error("Could not publish flash sale order request {}", request.reservationId(), ex);
        failReservation(request, "PUBLISH_FAILED");
        // Nếu bản tin thực ra đã tới và đơn đã được tạo, lượt giữ là CONFIRMED — trả về để client poll như thường
        if (reservationRepository.findById(request.reservationId())
                .filter(r -> r.getStatus() == FlashSaleReservation.Status.CONFIRMED).isPresent()) {
            return new PurchaseResult(request.reservationId(), ReservationStatus.PENDING);
        }
        throw new BusinessException(ErrorCode.FLASH_SALE_UNAVAILABLE);
    }

    private ItemInfo itemInfo(Long itemId) {
        ItemInfo cached = itemInfos.get(itemId);
        if (cached != null) {
            return cached;
        }
        FlashSaleItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FLASH_SALE_NOT_FOUND));
        ItemInfo info = new ItemInfo(item.getVariantId(), item.getSalePrice());
        itemInfos.put(itemId, info);
        return info;
    }

    /** Nạp quota của item lên Redis từ DB (chỉ khi phiên đang chạy/sắp chạy). */
    private void warmUp(Long itemId) {
        itemRepository.findWithSale(itemId).ifPresent(this::warmUp);
    }

    private void warmUp(FlashSaleItem item) {
        FlashSale sale = item.getFlashSale();
        Instant now = clock.instant();
        if (sale.getStatus() != FlashSale.Status.ACTIVE || !sale.getEndAt().isAfter(now)) {
            return;
        }
        Map<Long, Integer> bought = new HashMap<>();
        for (Object[] row : reservationRepository.confirmedQuantityByUser(item.getId())) {
            bought.put((Long) row[0], ((Number) row[1]).intValue());
        }
        stock.load(item.getId(), item.getQuota() - item.getSold(), item.getPerUserLimit(), sale.getStartAt(),
                sale.getEndAt(), bought, now);
    }

    private void warmUpQuietly(FlashSaleItem item) {
        try {
            warmUp(item);
        } catch (DataAccessException ex) {
            log.warn("Could not load flash sale item {} into Redis: {}", item.getId(), ex.getMessage());
        }
    }

    private void putState(UUID id, ReservationState state) {
        try {
            stock.putReservation(id, objectMapper.writeValueAsString(state), RESERVATION_TTL);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ReservationState readState(UUID id) {
        String json = stock.getReservation(id);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReservationState.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Map<Long, VariantSnapshot> variantsOf(List<FlashSale> sales) {
        List<Long> ids = sales.stream().flatMap(s -> s.getItems().stream()).map(FlashSaleItem::getVariantId).toList();
        return ids.isEmpty() ? Map.of() : catalogApi.findVariants(ids);
    }

    private FlashSaleView toView(FlashSale sale, Map<Long, VariantSnapshot> variants) {
        Instant now = clock.instant();
        List<FlashSaleItemView> items = sale.getItems().stream().map(i -> {
            VariantSnapshot v = variants.get(i.getVariantId());
            return new FlashSaleItemView(i.getId(), i.getVariantId(), i.getProductId(),
                    v == null ? null : v.productName(), v == null ? null : v.productSlug(),
                    v == null ? null : v.variantName(), v == null ? null : v.imageUrl(),
                    v == null ? i.getSalePrice() : v.price(), i.getSalePrice(), i.getQuota(), remaining(i),
                    i.getPerUserLimit());
        }).toList();
        return new FlashSaleView(sale.getId(), sale.getName(), sale.getStartAt(), sale.getEndAt(), sale.getStatus(),
                sale.isRunning(now), items);
    }

    /** Suất còn lại: số trên Redis khi đã nạp (gồm cả lượt đang giữ), không thì theo DB. */
    private int remaining(FlashSaleItem item) {
        try {
            Integer onRedis = stock.remaining(item.getId());
            if (onRedis != null) {
                return onRedis;
            }
        } catch (DataAccessException ex) {
            log.debug("Redis unavailable while reading flash sale stock: {}", ex.getMessage());
        }
        return item.getQuota() - item.getSold();
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private record ItemInfo(Long variantId, long salePrice) {
    }

    record ReservationState(Long userId, Long itemId, ReservationStatus status, String orderCode, String reason) {
    }

    public record ItemCommand(Long variantId, long salePrice, int quota, int perUserLimit) {
    }

    public record FlashSaleCommand(String name, Instant startAt, Instant endAt, List<ItemCommand> items) {
    }

    public record FlashSaleItemView(Long id, Long variantId, Long productId, String productName, String productSlug,
                                    String variantName, String imageUrl, long originalPrice, long salePrice, int quota,
                                    int remaining, int perUserLimit) {
    }

    public record FlashSaleView(Long id, String name, Instant startAt, Instant endAt, FlashSale.Status status,
                                boolean running, List<FlashSaleItemView> items) {
    }

    public record PurchaseResult(UUID reservationId, ReservationStatus status) {
    }

    public record ReservationView(UUID reservationId, Long itemId, ReservationStatus status, String orderCode,
                                  String reason) {
    }
}
