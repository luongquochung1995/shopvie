package com.shopvi.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Cấu hình vận chuyển (prefix {@code app.shipping}).
 *
 * @param itemWeightGrams       khối lượng ước tính mỗi sản phẩm (sản phẩm chưa có trường khối lượng)
 * @param defaultOriginProvince tỉnh lấy hàng mặc định khi shop chưa khai báo địa chỉ lấy hàng
 */
@ConfigurationProperties(prefix = "app.shipping")
public record ShippingProperties(int itemWeightGrams, String defaultOriginProvince, Rates rates, Ghn ghn) {

    public ShippingProperties {
        itemWeightGrams = itemWeightGrams <= 0 ? 500 : itemWeightGrams;
        defaultOriginProvince = StringUtils.hasText(defaultOriginProvince) ? defaultOriginProvince : "79";
        rates = rates == null ? new Rates(22_000, 30_000, 38_000, 5_000) : rates;
        ghn = ghn == null ? new Ghn(null, null, null, null) : ghn;
    }

    /**
     * Bảng phí nội bộ (VND) cho kiện ≤ 1kg, cộng thêm {@code extraPerHalfKg} cho mỗi 500g tiếp theo.
     * Vùng: Bắc (mã tỉnh &lt; 38), Trung &amp; Tây Nguyên (38–74), Nam (≥ 75).
     */
    public record Rates(long sameProvince, long sameRegion, long otherRegion, long extraPerHalfKg) {
    }

    /**
     * @param baseUrl      vd. https://dev-online-gateway.ghn.vn/shiip/public-api (sandbox)
     * @param webhookToken chuỗi bí mật gắn vào URL webhook đăng ký với GHN ({@code ?token=...})
     */
    public record Ghn(String token, String shopId, String baseUrl, String webhookToken) {
        public boolean configured() {
            return StringUtils.hasText(token) && StringUtils.hasText(shopId) && StringUtils.hasText(baseUrl);
        }
    }

    @Configuration
    @EnableConfigurationProperties(ShippingProperties.class)
    static class Config {
    }
}
