package com.shopvi.payment.application;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.common.util.Uuid7;
import com.shopvi.payment.PaymentApi;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.PaymentLink;
import com.shopvi.payment.PaymentRequest;
import com.shopvi.payment.PaymentState;
import com.shopvi.payment.PaymentSucceeded;
import com.shopvi.payment.application.PaymentProvider.CallbackData;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import com.shopvi.payment.infra.PaymentRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điều phối thanh toán online: tạo giao dịch, xác nhận kết quả từ return URL / IPN (idempotent theo mã giao dịch),
 * cổng giả lập cho dev/test.
 */
@Slf4j
@Service
public class PaymentService implements PaymentApi {

    private final PaymentRepository repository;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<PaymentChannel, PaymentProvider> providers = new EnumMap<>(PaymentChannel.class);

    public PaymentService(PaymentRepository repository, PaymentProperties properties, ApplicationEventPublisher events,
                          Clock clock, List<PaymentProvider> providers) {
        this.repository = repository;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        providers.forEach(p -> this.providers.put(p.channel(), p));
    }

    @Override
    public Duration paymentTtl() {
        return properties.ttl();
    }

    @Override
    public Set<PaymentChannel> availableChannels() {
        Set<PaymentChannel> channels = EnumSet.noneOf(PaymentChannel.class);
        providers.values().stream().filter(PaymentProvider::isEnabled).forEach(p -> channels.add(p.channel()));
        return channels;
    }

    @Override
    @Transactional
    public PaymentLink initiate(PaymentRequest req) {
        PaymentProvider provider = enabledProvider(req.channel());
        Instant expiresAt = req.expiresAt() != null ? req.expiresAt() : clock.instant().plus(properties.ttl());
        String key = Uuid7.next(clock).toString().replace("-", "");
        Payment payment = repository.save(Payment.start(req.checkoutId(), req.userId(), req.channel(), key,
                req.amount(), expiresAt));
        String url = properties.sandboxGateway()
                ? "%s/payment/sandbox?ref=%s&method=%s&amount=%d".formatted(properties.frontendBaseUrl(), key,
                req.channel(), req.amount())
                : provider.createPaymentUrl(payment, req.clientIp(), returnUrl(req.channel()), ipnUrl(req.channel()));
        return new PaymentLink(url, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentState> latestState(UUID checkoutId) {
        return repository.findFirstByCheckoutIdOrderByIdDesc(checkoutId).map(Payment::getStatus);
    }

    @Override
    @Transactional
    public void expire(UUID checkoutId) {
        repository.findByCheckoutIdAndStatus(checkoutId, PaymentState.PENDING).forEach(Payment::expire);
    }

    /**
     * Xác nhận kết quả từ cổng (return URL hoặc IPN — cùng một logic, gọi lặp an toàn).
     * Thanh toán thành công lần đầu thì phát {@link PaymentSucceeded} trong cùng transaction.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ConfirmResult confirm(PaymentChannel channel, Map<String, String> params) {
        CallbackData data;
        try {
            data = provider(channel).parseCallback(params);
        } catch (BusinessException ex) {
            log.warn("Rejected {} callback with invalid signature", channel);
            return new ConfirmResult(Outcome.INVALID_SIGNATURE, null);
        }
        Optional<Payment> found = data.reference() == null ? Optional.empty()
                : repository.findByIdempotencyKeyForUpdate(data.reference());
        if (found.isEmpty() || found.get().getProvider() != channel) {
            return new ConfirmResult(Outcome.NOT_FOUND, null);
        }
        Payment payment = found.get();
        if (payment.getAmount() != data.amount()) {
            log.warn("{} callback amount mismatch for {}: expected {}, got {}", channel, payment.getIdempotencyKey(),
                    payment.getAmount(), data.amount());
            return new ConfirmResult(Outcome.INVALID_AMOUNT, payment.getCheckoutId());
        }
        if (payment.isSucceeded() || payment.getStatus() == PaymentState.FAILED && !data.success()) {
            return new ConfirmResult(Outcome.ALREADY_CONFIRMED, payment.getCheckoutId(), payment.getStatus());
        }
        if (data.success()) {
            payment.succeed(data.providerTxnId(), data.raw(), clock.instant());
            events.publishEvent(new PaymentSucceeded(payment.getCheckoutId(), channel, payment.getAmount(),
                    data.providerTxnId()));
        } else {
            payment.fail(data.providerTxnId(), data.raw());
        }
        return new ConfirmResult(Outcome.CONFIRMED, payment.getCheckoutId(), payment.getStatus());
    }

    /**
     * Cổng giả lập (chỉ khi bật {@code app.payment.sandbox-gateway}): người mua chọn thành công/thất bại, ShopVi ký
     * callback như cổng thật và trả URL return để trình duyệt đi tiếp qua đúng luồng xác nhận.
     */
    @Transactional(readOnly = true)
    public String sandboxCallbackUrl(Long userId, String reference, boolean success) {
        if (!properties.sandboxGateway()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Payment payment = repository.findByIdempotencyKey(reference)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Map<String, String> params = provider(payment.getProvider()).signedCallback(payment, success, clock.instant());
        return returnUrl(payment.getProvider()) + "?" + params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** Trang kết quả trên frontend mà người mua được chuyển tới sau khi rời cổng thanh toán. */
    public String frontendResultUrl(ConfirmResult result) {
        String status = switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> result.state() == PaymentState.SUCCESS ? "success" : "failed";
            case INVALID_SIGNATURE, NOT_FOUND, INVALID_AMOUNT -> "invalid";
        };
        String url = properties.frontendBaseUrl() + "/payment/result?status=" + status;
        return result.checkoutId() == null ? url : url + "&checkoutId=" + result.checkoutId();
    }

    private PaymentProvider enabledProvider(PaymentChannel channel) {
        PaymentProvider provider = providers.get(channel);
        if (provider == null || !provider.isEnabled()) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        return provider;
    }

    private PaymentProvider provider(PaymentChannel channel) {
        PaymentProvider provider = providers.get(channel);
        if (provider == null) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        return provider;
    }

    private String returnUrl(PaymentChannel channel) {
        return properties.publicBaseUrl() + "/api/v1/payments/" + channel.name().toLowerCase(Locale.ROOT) + "/return";
    }

    private String ipnUrl(PaymentChannel channel) {
        return properties.publicBaseUrl() + "/api/v1/payments/" + channel.name().toLowerCase(Locale.ROOT) + "/ipn";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public enum Outcome { CONFIRMED, ALREADY_CONFIRMED, NOT_FOUND, INVALID_AMOUNT, INVALID_SIGNATURE }

    /** @param state trạng thái giao dịch sau khi xử lý (null nếu không tìm thấy / sai chữ ký) */
    public record ConfirmResult(Outcome outcome, UUID checkoutId, PaymentState state) {
        ConfirmResult(Outcome outcome, UUID checkoutId) {
            this(outcome, checkoutId, null);
        }
    }
}
