package com.shopvi.payment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MomoProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final String ENDPOINT = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final PaymentProperties PROPS = new PaymentProperties("https://api.shopvi.test",
            "https://shopvi.test", null, false, null,
            new PaymentProperties.Momo("MOMOTEST", "access", "secret", ENDPOINT));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MomoProvider provider = new MomoProvider(PROPS, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Payment payment = Payment.start(UUID.fromString("0199a0b0-0000-7000-8000-000000000002"), 7L,
            PaymentChannel.MOMO, "ref42", 250_000, NOW.plusSeconds(900));

    @Test
    void createsCaptureWalletPaymentWithSignatureAndReturnsPayUrl() {
        String raw = "accessKey=access&amount=250000&extraData=&ipnUrl=https://api.shopvi.test/ipn&orderId=ref42"
                + "&orderInfo=Thanh toan don hang ShopVi 0199a0b0-0000-7000-8000-000000000002&partnerCode=MOMOTEST"
                + "&redirectUrl=https://api.shopvi.test/return&requestId=ref42&requestType=captureWallet";
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.partnerCode").value("MOMOTEST"))
                .andExpect(jsonPath("$.amount").value(250_000))
                .andExpect(jsonPath("$.requestType").value("captureWallet"))
                .andExpect(jsonPath("$.orderExpireTime").value(15))
                .andExpect(jsonPath("$.signature").value(Hmac.sha256("secret", raw)))
                .andRespond(withSuccess("{\"resultCode\":0,\"payUrl\":\"https://test-payment.momo.vn/pay/abc\"}",
                        MediaType.APPLICATION_JSON));

        String url = provider.createPaymentUrl(payment, "1.2.3.4", "https://api.shopvi.test/return",
                "https://api.shopvi.test/ipn");

        assertThat(url).isEqualTo("https://test-payment.momo.vn/pay/abc");
        server.verify();
    }

    @Test
    void gatewayErrorsBecomeProviderError() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess("{\"resultCode\":13,\"message\":\"Auth failed\"}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> provider.createPaymentUrl(payment, null, "r", "i"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR);

        server.reset();
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());
        assertThatThrownBy(() -> provider.createPaymentUrl(payment, null, "r", "i"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PAYMENT_PROVIDER_ERROR);
    }

    @Test
    void callbackSignatureIsVerified() {
        Map<String, String> callback = provider.signedCallback(payment, true, NOW);

        var data = provider.parseCallback(callback);
        assertThat(data.success()).isTrue();
        assertThat(data.amount()).isEqualTo(250_000);
        assertThat(data.reference()).isEqualTo("ref42");

        Map<String, String> tampered = new HashMap<>(callback);
        tampered.put("resultCode", "0");
        tampered.put("amount", "1");
        assertThatThrownBy(() -> provider.parseCallback(tampered))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_SIGNATURE);

        assertThat(provider.parseCallback(provider.signedCallback(payment, false, NOW)).success()).isFalse();
    }
}
