package com.shopvi.catalog;

import org.springframework.modulith.events.Externalized;

/**
 * Sự kiện: dữ liệu hiển thị/tìm kiếm của sản phẩm đã đổi (tạo, sửa, ẩn/hiện, xóa, số đã bán, điểm đánh giá).
 * Được Spring Modulith lưu vào event publication registry trong cùng transaction rồi đẩy sang Kafka topic
 * {@value #TOPIC} sau khi commit (key = id sản phẩm để các bản tin của một sản phẩm giữ thứ tự).
 */
@Externalized(ProductChanged.TOPIC + "::#{#this.productId().toString()}")
public record ProductChanged(Long productId) {

    public static final String TOPIC = "shopvi.product-changed";
}
