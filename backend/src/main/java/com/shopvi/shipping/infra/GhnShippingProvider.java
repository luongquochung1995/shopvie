package com.shopvi.shipping.infra;

import com.shopvi.common.exception.BusinessException;
import com.shopvi.common.exception.ErrorCode;
import com.shopvi.shipping.Location;
import com.shopvi.shipping.Party;
import com.shopvi.shipping.ShipmentStatus;
import com.shopvi.shipping.ShippingApi.Item;
import com.shopvi.shipping.application.ShippingProvider;
import com.shopvi.shipping.config.ShippingProperties;
import com.shopvi.shipping.domain.Shipment;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Giao Hàng Nhanh (API {@code /shiip/public-api/v2}). Địa chỉ gửi theo địa giới 2 cấp: tên tỉnh + tên phường/xã
 * (không có quận/huyện) và cờ địa chỉ mới.
 * <p><b>Chưa kiểm chứng trên sandbox:</b> tài liệu GHN cho địa giới 2 cấp không công khai; tên trường
 * {@code to_ward_name}/{@code to_province_name}/{@code is_new_to_address} lấy theo hướng dẫn của GHN về đơn vị hành
 * chính mới và tài liệu cộng đồng. Tính phí lỗi → service dùng bảng phí nội bộ.</p>
 */
@Slf4j
@Component
class GhnShippingProvider implements ShippingProvider {

    /** Gói dịch vụ chuẩn (hàng nhẹ). */
    static final int SERVICE_TYPE_STANDARD = 2;
    /** Người gửi (shop) trả phí ship cho GHN — người mua đã trả phí ship cho sàn khi đặt hàng. */
    static final int PAYMENT_TYPE_SENDER = 1;

    private final ShippingProperties properties;
    private final Divisions divisions;
    private final RestClient rest;

    GhnShippingProvider(ShippingProperties properties, Divisions divisions, RestClient.Builder builder) {
        this.properties = properties;
        this.divisions = divisions;
        // Timeout theo spring.http.client.* (application.yml)
        this.rest = builder.build();
    }

    @Override
    public Shipment.Provider id() {
        return Shipment.Provider.GHN;
    }

    @Override
    public boolean isEnabled() {
        return properties.ghn().configured();
    }

    @Override
    public long quote(Location origin, Location destination, int weightGrams, long itemsValue) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service_type_id", SERVICE_TYPE_STANDARD);
        putLocation(body, "from_", origin);
        putLocation(body, "to_", destination);
        body.put("is_new_to_address", true);
        body.put("weight", weightGrams);
        body.put("insurance_value", itemsValue);
        Map<?, ?> data = asMap(call("/v2/shipping-order/fee", body));
        return toLong(data.get("total"));
    }

    @Override
    public Created create(String orderCode, Party from, Party to, int weightGrams, long itemsValue, long codAmount,
                          String note, Collection<Item> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("payment_type_id", PAYMENT_TYPE_SENDER);
        body.put("service_type_id", SERVICE_TYPE_STANDARD);
        body.put("required_note", "CHOXEMHANGKHONGTHU");
        body.put("client_order_code", orderCode);
        body.put("from_name", from.name());
        body.put("from_phone", from.phone());
        body.put("from_address", from.detail());
        putLocation(body, "from_", from.location());
        body.put("to_name", to.name());
        body.put("to_phone", to.phone());
        body.put("to_address", to.detail());
        putLocation(body, "to_", to.location());
        body.put("is_new_to_address", true);
        body.put("cod_amount", codAmount);
        body.put("insurance_value", itemsValue);
        body.put("weight", weightGrams);
        body.put("length", 20);
        body.put("width", 15);
        body.put("height", 10);
        body.put("note", note == null ? "" : note);
        body.put("items", items.stream().map(i -> Map.of("name", i.name(), "quantity", i.quantity())).toList());
        Map<?, ?> data = asMap(call("/v2/shipping-order/create", body));
        Object code = data.get("order_code");
        if (code == null) {
            throw new BusinessException(ErrorCode.SHIPPING_PROVIDER_ERROR);
        }
        return new Created(code.toString(), data.get("total_fee") == null ? null : toLong(data.get("total_fee")),
                parseTime(data.get("expected_delivery_time")).orElse(null));
    }

    @Override
    public void cancel(String trackingCode) {
        call("/v2/switch-status/cancel", Map.of("order_codes", List.of(trackingCode)));
    }

    /**
     * Trạng thái GHN → trạng thái chuẩn hóa (empty = bản tin không làm đổi trạng thái, vd. cập nhật cân nặng).
     * Danh sách trạng thái theo tài liệu webhook của GHN.
     */
    static Optional<ShipmentStatus> mapStatus(String ghnStatus) {
        if (ghnStatus == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (ghnStatus.toLowerCase(Locale.ROOT)) {
            case "ready_to_pick", "picking" -> ShipmentStatus.CREATED;
            case "picked", "money_collect_picking" -> ShipmentStatus.PICKED_UP;
            case "storing", "transporting", "sorting", "delivering", "money_collect_delivering" ->
                    ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "delivery_fail" -> ShipmentStatus.FAILED;
            case "waiting_to_return", "return", "return_transporting", "return_sorting", "returning", "returned" ->
                    ShipmentStatus.RETURNED;
            case "cancel" -> ShipmentStatus.CANCELLED;
            default -> null;
        });
    }

    static Optional<Instant> parseTime(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value.toString()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private void putLocation(Map<String, Object> body, String prefix, Location location) {
        body.put(prefix + "province_name", divisions.provinceName(location.provinceCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ADDRESS)));
        if (location.wardCode() != null) {
            body.put(prefix + "ward_name", divisions.wardName(location.provinceCode(), location.wardCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ADDRESS)));
        }
    }

    /** Gọi API, trả trường {@code data} (object hoặc mảng tùy endpoint). */
    private Object call(String path, Object body) {
        ShippingProperties.Ghn ghn = properties.ghn();
        try {
            Map<?, ?> res = rest.post().uri(ghn.baseUrl() + path)
                    .header("Token", ghn.token())
                    .header("ShopId", ghn.shopId())
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (res == null || !"200".equals(String.valueOf(res.get("code")))) {
                log.warn("GHN {} failed: {}", path, res == null ? null : res.get("message"));
                throw new BusinessException(ErrorCode.SHIPPING_PROVIDER_ERROR);
            }
            return res.get("data");
        } catch (RestClientException ex) {
            log.warn("GHN {} error: {}", path, ex.getMessage());
            throw new BusinessException(ErrorCode.SHIPPING_PROVIDER_ERROR);
        }
    }

    private static Map<?, ?> asMap(Object data) {
        if (data instanceof Map<?, ?> map) {
            return map;
        }
        throw new BusinessException(ErrorCode.SHIPPING_PROVIDER_ERROR);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        throw new BusinessException(ErrorCode.SHIPPING_PROVIDER_ERROR);
    }
}
