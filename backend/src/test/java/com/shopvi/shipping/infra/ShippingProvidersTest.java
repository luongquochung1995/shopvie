package com.shopvi.shipping.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shipping.Location;
import com.shopvi.shipping.Party;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShippingApi.Item;
import com.shopvi.shipping.config.ShippingProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ShippingProvidersTest {

    private static final String BASE = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    private static final ShippingProperties.Rates RATES = new ShippingProperties.Rates(22_000, 30_000, 38_000, 5_000);
    private static final Divisions DIVISIONS = new Divisions(new ObjectMapper());

    @Test
    void internalFeeDependsOnZoneAndWeight() {
        // HCM → HCM / HCM → Cần Thơ (cùng miền Nam) / HCM → Hà Nội (khác miền)
        assertThat(InternalShippingProvider.fee("79", "79", 500, RATES)).isEqualTo(22_000);
        assertThat(InternalShippingProvider.fee("79", "92", 1000, RATES)).isEqualTo(30_000);
        assertThat(InternalShippingProvider.fee("79", "01", 1000, RATES)).isEqualTo(38_000);
        // 1,6kg: vượt 600g → 2 bước 500g
        assertThat(InternalShippingProvider.fee("79", "79", 1600, RATES)).isEqualTo(32_000);
        // Không rõ tỉnh lấy hàng → tính như khác miền
        assertThat(InternalShippingProvider.fee(null, "79", 500, RATES)).isEqualTo(38_000);
    }

    @Test
    void regionsFollowProvinceCodes() {
        assertThat(Divisions.region("01")).isZero();
        assertThat(Divisions.region("48")).isEqualTo(1);
        assertThat(Divisions.region("68")).isEqualTo(1);
        assertThat(Divisions.region("75")).isEqualTo(2);
        assertThat(DIVISIONS.provinceName("79")).contains("Thành phố Hồ Chí Minh");
        assertThat(DIVISIONS.wardName("79", "26740")).contains("Phường Sài Gòn");
        assertThat(DIVISIONS.wardName("01", "26740")).isEmpty();
    }

    @Test
    void ghnQuoteAndCreateSendTwoLevelAddressNames() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GhnShippingProvider ghn = new GhnShippingProvider(new ShippingProperties(500, "79", RATES,
                new ShippingProperties.Ghn("tok", "885", BASE, "hook")), DIVISIONS, builder);

        server.expect(requestTo(BASE + "/v2/shipping-order/fee"))
                .andExpect(header("Token", "tok")).andExpect(header("ShopId", "885"))
                .andExpect(jsonPath("$.to_province_name").value("Thành phố Hà Nội"))
                .andExpect(jsonPath("$.to_ward_name").value("Phường Ba Đình"))
                .andExpect(jsonPath("$.from_province_name").value("Thành phố Hồ Chí Minh"))
                .andExpect(jsonPath("$.weight").value(1000))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"total\":36500}}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE + "/v2/shipping-order/create"))
                .andExpect(jsonPath("$.client_order_code").value("order-1"))
                .andExpect(jsonPath("$.to_name").value("Nguyễn A"))
                .andExpect(jsonPath("$.cod_amount").value(230_000))
                .andExpect(jsonPath("$.is_new_to_address").value(true))
                .andExpect(jsonPath("$.items[0].name").value("Áo"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"order_code\":\"GHN123\",\"total_fee\":36500,"
                        + "\"expected_delivery_time\":\"2026-10-29T16:59:59Z\"}}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE + "/v2/shipping-order/fee"))
                .andRespond(withSuccess("{\"code\":400,\"message\":\"invalid\"}", MediaType.APPLICATION_JSON));

        // Khai báo đủ expectation rồi mới gọi
        assertThat(ghn.quote(new Location("79", "26740"), new Location("01", "00004"), 1000, 200_000)).isEqualTo(36_500);
        var created = ghn.create("order-1", new Party("Shop", "0901", "79", "26740", "12 Lê Lợi"),
                new Party("Nguyễn A", "0912", "01", "00004", "1 Kim Mã"), 1000, 200_000, 230_000, null,
                List.of(new Item("Áo", 2)));
        assertThat(created.trackingCode()).isEqualTo("GHN123");
        assertThat(created.expectedDeliveryAt()).isEqualTo(Instant.parse("2026-10-29T16:59:59Z"));
        assertThatThrownBy(() -> ghn.quote(new Location("79", null), new Location("01", "00004"), 500, 1))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SHIPPING_PROVIDER_ERROR);
        // Mã địa giới không hợp lệ → không gọi GHN
        assertThatThrownBy(() -> ghn.quote(new Location("79", null), new Location("01", "99999"), 500, 1))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_ADDRESS);
        server.verify();
    }

    @Test
    void ghnStatusesAreNormalized() {
        assertThat(GhnShippingProvider.mapStatus("ready_to_pick")).contains(ShipmentStatus.CREATED);
        assertThat(GhnShippingProvider.mapStatus("picked")).contains(ShipmentStatus.PICKED_UP);
        assertThat(GhnShippingProvider.mapStatus("delivering")).contains(ShipmentStatus.IN_TRANSIT);
        assertThat(GhnShippingProvider.mapStatus("delivered")).contains(ShipmentStatus.DELIVERED);
        assertThat(GhnShippingProvider.mapStatus("returned")).contains(ShipmentStatus.RETURNED);
        assertThat(GhnShippingProvider.mapStatus("cancel")).contains(ShipmentStatus.CANCELLED);
        assertThat(GhnShippingProvider.mapStatus("update_weight")).isEmpty();

        var update = new GhnWebhookParser().parse(Map.of("OrderCode", "GHN123", "Status", "delivered",
                "Time", "2026-10-28T08:00:00Z", "Description", "Giao thành công"), Instant.EPOCH);
        assertThat(update).hasValueSatisfying(u -> {
            assertThat(u.trackingCode()).isEqualTo("GHN123");
            assertThat(u.status()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(u.occurredAt()).isEqualTo(Instant.parse("2026-10-28T08:00:00Z"));
        });
    }
}
