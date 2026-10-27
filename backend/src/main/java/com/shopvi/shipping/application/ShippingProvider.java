package com.shopvi.shipping.application;

import com.shopvi.shipping.Location;
import com.shopvi.shipping.Party;
import com.shopvi.shipping.ShippingApi.Item;
import com.shopvi.shipping.domain.Shipment;
import java.time.Instant;
import java.util.Collection;

/** Hãng vận chuyển (strategy pattern). */
public interface ShippingProvider {

    Shipment.Provider id();

    boolean isEnabled();

    /** Phí ship (VND); hãng lỗi thì ném exception để service dùng bảng phí dự phòng. */
    long quote(Location origin, Location destination, int weightGrams, long itemsValue);

    Created create(String orderCode, Party from, Party to, int weightGrams, long itemsValue, long codAmount,
                   String note, Collection<Item> items);

    void cancel(String trackingCode);

    record Created(String trackingCode, Long fee, Instant expectedDeliveryAt) {
    }
}
