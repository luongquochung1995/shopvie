package com.shopvi.shipping.infra;

import com.shopvi.shipping.Location;
import com.shopvi.shipping.Party;
import com.shopvi.shipping.ShippingApi.Item;
import com.shopvi.shipping.application.ShippingProvider;
import com.shopvi.shipping.config.ShippingProperties;
import com.shopvi.shipping.domain.Shipment;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Vận chuyển nội bộ (luôn sẵn sàng): phí theo vùng (cùng tỉnh / cùng miền / khác miền) + khối lượng,
 * mã vận đơn do ShopVi sinh, seller tự cập nhật hành trình. Là phương án dự phòng khi GHN chưa cấu hình / lỗi.
 */
@Component
@RequiredArgsConstructor
class InternalShippingProvider implements ShippingProvider {

    private final ShippingProperties properties;
    private final ShipmentRepository repository;
    private final Clock clock;

    @Override
    public Shipment.Provider id() {
        return Shipment.Provider.INTERNAL;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public long quote(Location origin, Location destination, int weightGrams, long itemsValue) {
        return fee(origin.provinceCode(), destination.provinceCode(), weightGrams, properties.rates());
    }

    /** Phí cho kiện ≤ 1kg theo vùng, mỗi 500g tiếp theo cộng thêm {@code extraPerHalfKg}. */
    static long fee(String fromProvince, String toProvince, int weightGrams, ShippingProperties.Rates rates) {
        long base;
        if (Objects.equals(fromProvince, toProvince)) {
            base = rates.sameProvince();
        } else if (Divisions.region(fromProvince) >= 0 && Divisions.region(fromProvince) == Divisions.region(toProvince)) {
            base = rates.sameRegion();
        } else {
            base = rates.otherRegion();
        }
        int extraSteps = weightGrams <= 1000 ? 0 : (weightGrams - 1000 + 499) / 500;
        return base + extraSteps * rates.extraPerHalfKg();
    }

    @Override
    public Created create(String orderCode, Party from, Party to, int weightGrams, long itemsValue, long codAmount,
                          String note, Collection<Item> items) {
        String tracking;
        do {
            tracking = "SVX" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
        } while (repository.existsByTrackingCode(tracking));
        // Dự kiến: cùng tỉnh 1 ngày, cùng miền 2 ngày, khác miền 4 ngày
        int days = Objects.equals(from.provinceCode(), to.provinceCode()) ? 1
                : Divisions.region(from.provinceCode()) == Divisions.region(to.provinceCode()) ? 2 : 4;
        return new Created(tracking, fee(from.provinceCode(), to.provinceCode(), weightGrams, properties.rates()),
                clock.instant().plus(Duration.ofDays(days)));
    }

    @Override
    public void cancel(String trackingCode) {
        // Vận đơn nội bộ: không cần báo hãng
    }
}
