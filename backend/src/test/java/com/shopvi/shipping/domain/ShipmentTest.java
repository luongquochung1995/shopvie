package com.shopvi.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopvi.shipping.ShipmentStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    private static final Instant T0 = Instant.parse("2026-10-26T03:00:00Z");

    @Test
    void recordsJourneyIgnoresDuplicatesAndStopsWhenFinal() {
        Shipment s = Shipment.create("order-1", Shipment.Provider.GHN, "GHN1", 30_000L, null, "Tạo vận đơn", T0);

        assertThat(s.record(ShipmentStatus.PICKED_UP, "picked", "Đã lấy hàng", T0.plusSeconds(60), null)).isTrue();
        // Webhook gửi lặp cùng bản tin → bỏ qua
        assertThat(s.record(ShipmentStatus.PICKED_UP, "picked", "Đã lấy hàng", T0.plusSeconds(60), null)).isFalse();
        // Cùng trạng thái chuẩn hóa nhưng bản tin mới → ghi hành trình, trạng thái không đổi
        assertThat(s.record(ShipmentStatus.IN_TRANSIT, "storing", "Nhập kho", T0.plusSeconds(120), null)).isTrue();
        assertThat(s.record(ShipmentStatus.IN_TRANSIT, "delivering", "Đang giao", T0.plusSeconds(180), null)).isFalse();
        assertThat(s.record(ShipmentStatus.DELIVERED, "delivered", "Giao thành công", T0.plusSeconds(240), null)).isTrue();
        assertThat(s.record(ShipmentStatus.RETURNED, "return", "Hoàn", T0.plusSeconds(300), null)).isFalse();

        var view = s.toView();
        assertThat(view.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(view.events()).hasSize(5);
        // Mới nhất trước
        assertThat(view.events().get(0).status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(view.events().get(4).status()).isEqualTo(ShipmentStatus.CREATED);
    }
}
