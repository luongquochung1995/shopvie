package com.shopvi.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.PaymentRequest;
import com.shopvi.payment.PaymentState;
import com.shopvi.payment.PaymentSucceeded;
import com.shopvi.payment.application.PaymentService.Outcome;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import com.shopvi.payment.infra.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final UUID CHECKOUT = UUID.fromString("0199a0b0-0000-7000-8000-000000000003");

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeProvider vnpay = new FakeProvider();
    private Payment payment;

    private PaymentService service(boolean sandbox) {
        PaymentProperties props = new PaymentProperties("https://api.shopvi.test", "https://shopvi.test",
                Duration.ofMinutes(15), sandbox, null, null);
        return new PaymentService(repository, props, events, clock, List.of(vnpay));
    }

    @BeforeEach
    void setUp() {
        payment = Payment.start(CHECKOUT, 7L, PaymentChannel.VNPAY, "ref1", 100_000, NOW.plusSeconds(900));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByIdempotencyKeyForUpdate("ref1")).thenReturn(Optional.of(payment));
        when(repository.findByIdempotencyKey("ref1")).thenReturn(Optional.of(payment));
    }

    @Test
    void initiateUsesProviderUrlOrSandboxPage() {
        var link = service(false).initiate(new PaymentRequest(CHECKOUT, 7L, PaymentChannel.VNPAY, 100_000, "1.1.1.1", null));
        assertThat(link.paymentUrl()).isEqualTo("https://gateway.test/pay");
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(vnpay.lastReturnUrl).isEqualTo("https://api.shopvi.test/api/v1/payments/vnpay/return");
        assertThat(vnpay.lastIpnUrl).isEqualTo("https://api.shopvi.test/api/v1/payments/vnpay/ipn");

        Instant deadline = NOW.plusSeconds(60);
        var sandbox = service(true).initiate(new PaymentRequest(CHECKOUT, 7L, PaymentChannel.VNPAY, 100_000, null, deadline));
        assertThat(sandbox.paymentUrl()).startsWith("https://shopvi.test/payment/sandbox?ref=")
                .contains("&method=VNPAY&amount=100000");
        assertThat(sandbox.expiresAt()).isEqualTo(deadline);
    }

    @Test
    void disabledOrMissingChannelIsNotSupported() {
        vnpay.enabled = false;
        var service = service(false);

        assertThat(service.availableChannels()).isEmpty();
        assertThatThrownBy(() -> service.initiate(new PaymentRequest(CHECKOUT, 7L, PaymentChannel.MOMO, 1, null, null)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
    }

    @Test
    void successIsConfirmedOnceAndRepeatedCallbacksAreIdempotent() {
        var service = service(false);

        var first = service.confirm(PaymentChannel.VNPAY, callback("ref1", true, 100_000));
        var second = service.confirm(PaymentChannel.VNPAY, callback("ref1", true, 100_000));

        assertThat(first.outcome()).isEqualTo(Outcome.CONFIRMED);
        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY_CONFIRMED);
        assertThat(payment.getStatus()).isEqualTo(PaymentState.SUCCESS);
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events, times(1)).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new PaymentSucceeded(CHECKOUT, PaymentChannel.VNPAY, 100_000, "TXN"));
        assertThat(service.frontendResultUrl(second))
                .isEqualTo("https://shopvi.test/payment/result?status=success&checkoutId=" + CHECKOUT);
    }

    @Test
    void failureThenLaterSuccessStillCountsBecauseMoneyWasTaken() {
        var service = service(false);

        var failed = service.confirm(PaymentChannel.VNPAY, callback("ref1", false, 100_000));
        assertThat(failed.state()).isEqualTo(PaymentState.FAILED);
        assertThat(service.frontendResultUrl(failed)).contains("status=failed");
        assertThat(service.confirm(PaymentChannel.VNPAY, callback("ref1", false, 100_000)).outcome())
                .isEqualTo(Outcome.ALREADY_CONFIRMED);
        verify(events, never()).publishEvent(any());

        assertThat(service.confirm(PaymentChannel.VNPAY, callback("ref1", true, 100_000)).state())
                .isEqualTo(PaymentState.SUCCESS);
        verify(events).publishEvent(any(PaymentSucceeded.class));
    }

    @Test
    void wrongAmountUnknownReferenceAndBadSignatureAreRejected() {
        var service = service(false);

        assertThat(service.confirm(PaymentChannel.VNPAY, callback("ref1", true, 1)).outcome())
                .isEqualTo(Outcome.INVALID_AMOUNT);
        assertThat(service.confirm(PaymentChannel.VNPAY, callback("unknown", true, 100_000)).outcome())
                .isEqualTo(Outcome.NOT_FOUND);
        Map<String, String> bad = callback("ref1", true, 100_000);
        bad.put("sig", "forged");
        var invalid = service.confirm(PaymentChannel.VNPAY, bad);
        assertThat(invalid.outcome()).isEqualTo(Outcome.INVALID_SIGNATURE);
        assertThat(service.frontendResultUrl(invalid)).isEqualTo("https://shopvi.test/payment/result?status=invalid");
        assertThat(payment.getStatus()).isEqualTo(PaymentState.PENDING);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void sandboxCallbackOnlyWhenEnabledAndForTheOwner() {
        assertThatThrownBy(() -> service(false).sandboxCallbackUrl(7L, "ref1", true))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service(true).sandboxCallbackUrl(99L, "ref1", true))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        assertThat(service(true).sandboxCallbackUrl(7L, "ref1", true))
                .startsWith("https://api.shopvi.test/api/v1/payments/vnpay/return?")
                .contains("ref=ref1", "ok=true", "sig=valid");
    }

    private static Map<String, String> callback(String ref, boolean ok, long amount) {
        Map<String, String> params = new HashMap<>();
        params.put("ref", ref);
        params.put("ok", String.valueOf(ok));
        params.put("amount", String.valueOf(amount));
        params.put("sig", "valid");
        return params;
    }

    /** Cổng giả: chữ ký hợp lệ khi sig = "valid". */
    static class FakeProvider implements PaymentProvider {
        boolean enabled = true;
        String lastReturnUrl;
        String lastIpnUrl;

        @Override
        public PaymentChannel channel() {
            return PaymentChannel.VNPAY;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public String createPaymentUrl(Payment payment, String clientIp, String returnUrl, String ipnUrl) {
            lastReturnUrl = returnUrl;
            lastIpnUrl = ipnUrl;
            return "https://gateway.test/pay";
        }

        @Override
        public CallbackData parseCallback(Map<String, String> params) {
            if (!"valid".equals(params.get("sig"))) {
                throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
            }
            return new CallbackData(params.get("ref"), Boolean.parseBoolean(params.get("ok")),
                    Long.parseLong(params.get("amount")), "TXN", "00", Map.copyOf(params));
        }

        @Override
        public Map<String, String> signedCallback(Payment payment, boolean success, Instant now) {
            return new java.util.TreeMap<>(callback(payment.getIdempotencyKey(), success, payment.getAmount()));
        }
    }
}
