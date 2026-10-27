package com.shopvi.payment.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.payment.PaymentChannel;
import com.shopvi.payment.config.PaymentProperties;
import com.shopvi.payment.domain.Payment;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VnpayProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");
    private static final PaymentProperties PROPS = new PaymentProperties("https://api.shopvi.test",
            "https://shopvi.test", null, false,
            new PaymentProperties.Vnpay("TESTTMN", "secret-key", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"),
            null);

    private final VnpayProvider provider = new VnpayProvider(PROPS, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Payment payment = Payment.start(UUID.fromString("0199a0b0-0000-7000-8000-000000000001"), 7L,
            PaymentChannel.VNPAY, "abc123", 189_000, NOW.plusSeconds(900));

    @Test
    void paymentUrlHasRequiredParamsAndValidSignature() {
        String url = provider.createPaymentUrl(payment, "203.0.113.5", "https://api.shopvi.test/return",
                "https://api.shopvi.test/ipn");

        Map<String, String> params = query(url);
        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(params).containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "TESTTMN")
                .containsEntry("vnp_Amount", "18900000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_TxnRef", "abc123")
                .containsEntry("vnp_IpAddr", "203.0.113.5")
                // Giờ Việt Nam (GMT+7)
                .containsEntry("vnp_CreateDate", "20261026100000")
                .containsEntry("vnp_ExpireDate", "20261026101500");
        // Ký trên chuỗi đã sắp xếp, giá trị encode US-ASCII (khoảng trắng → '+')
        assertThat(url).contains("vnp_OrderInfo=Thanh+toan+don+hang+ShopVi+");
        Map<String, String> unsigned = new TreeMap<>(params);
        unsigned.remove("vnp_SecureHash");
        assertThat(params.get("vnp_SecureHash")).isEqualTo(Hmac.sha512("secret-key", VnpayProvider.encode(unsigned)));
    }

    @Test
    void signedCallbackRoundTripsAndDetectsTampering() {
        Map<String, String> callback = provider.signedCallback(payment, true, NOW);

        var data = provider.parseCallback(callback);
        assertThat(data.success()).isTrue();
        assertThat(data.amount()).isEqualTo(189_000);
        assertThat(data.reference()).isEqualTo("abc123");
        assertThat(data.providerTxnId()).isNotBlank();

        Map<String, String> tampered = new HashMap<>(callback);
        tampered.put("vnp_Amount", "100");
        assertThatThrownBy(() -> provider.parseCallback(tampered))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_SIGNATURE);

        Map<String, String> unsigned = new HashMap<>(callback);
        unsigned.remove("vnp_SecureHash");
        assertThatThrownBy(() -> provider.parseCallback(unsigned)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancelledByCustomerIsNotSuccessful() {
        var data = provider.parseCallback(provider.signedCallback(payment, false, NOW));

        assertThat(data.success()).isFalse();
        assertThat(data.resultCode()).isEqualTo("24");
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.US_ASCII));
        }
        return params;
    }
}
