package com.shopvi.order.infra;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn thống kê đơn hàng của một shop trong khoảng [start, end). Đơn CANCELLED/RETURNED không tính doanh số.
 */
@Repository
@RequiredArgsConstructor
public class SellerStatsQueries {

    private static final String COUNTED = "o.status NOT IN ('CANCELLED', 'RETURNED')";

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Doanh số (tổng tiền hàng, không gồm phí ship), số đơn và số đơn hủy theo từng kỳ (ngày/tháng theo múi giờ
     * {@code zone}). Kỳ không có đơn không có trong kết quả.
     *
     * @param unit {@code day} hoặc {@code month} (đơn vị của {@code date_trunc})
     */
    public List<PeriodRow> periods(Long shopId, Instant start, Instant end, String unit, ZoneId zone) {
        String sql = """
                SELECT date_trunc(:unit, o.created_at AT TIME ZONE :zone) AS period,
                       COALESCE(SUM(o.subtotal) FILTER (WHERE %1$s), 0) AS revenue,
                       COUNT(*) FILTER (WHERE %1$s) AS orders,
                       COUNT(*) FILTER (WHERE o.status = 'CANCELLED') AS cancelled
                FROM orders o
                WHERE o.shop_id = :shopId AND o.created_at >= :start AND o.created_at < :end
                GROUP BY 1 ORDER BY 1""".formatted(COUNTED);
        return jdbc.query(sql, range(shopId, start, end).addValue("unit", unit).addValue("zone", zone.getId()),
                (rs, i) -> new PeriodRow(rs.getObject("period", LocalDateTime.class), rs.getLong("revenue"),
                        rs.getLong("orders"), rs.getLong("cancelled")));
    }

    /** Tổng số sản phẩm đã bán (đơn chưa hủy) trong khoảng. */
    public long unitsSold(Long shopId, Instant start, Instant end) {
        Long units = jdbc.queryForObject("""
                SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi JOIN orders o ON o.id = oi.order_id
                WHERE o.shop_id = :shopId AND o.created_at >= :start AND o.created_at < :end AND %s"""
                .formatted(COUNTED), range(shopId, start, end), Long.class);
        return units == null ? 0 : units;
    }

    /** Sản phẩm bán chạy theo số lượng; tên/slug/ảnh lấy từ snapshot mới nhất trong đơn hàng. */
    public List<TopProductRow> topProducts(Long shopId, Instant start, Instant end, int limit) {
        String sql = """
                SELECT oi.product_id,
                       (array_agg(oi.product_name ORDER BY oi.id DESC))[1] AS product_name,
                       (array_agg(oi.product_slug ORDER BY oi.id DESC))[1] AS product_slug,
                       (array_agg(oi.image_url ORDER BY oi.id DESC))[1] AS image_url,
                       SUM(oi.quantity) AS quantity,
                       SUM(oi.unit_price * oi.quantity) AS revenue
                FROM order_items oi JOIN orders o ON o.id = oi.order_id
                WHERE o.shop_id = :shopId AND o.created_at >= :start AND o.created_at < :end AND %s
                GROUP BY oi.product_id
                ORDER BY quantity DESC, revenue DESC, oi.product_id
                LIMIT :limit""".formatted(COUNTED);
        return jdbc.query(sql, range(shopId, start, end).addValue("limit", limit),
                (rs, i) -> new TopProductRow(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getString("product_slug"), rs.getString("image_url"), rs.getLong("quantity"),
                        rs.getLong("revenue")));
    }

    private static MapSqlParameterSource range(Long shopId, Instant start, Instant end) {
        // OffsetDateTime → kiểu timestamptz của PostgreSQL (không phụ thuộc múi giờ của phiên kết nối)
        return new MapSqlParameterSource("shopId", shopId)
                .addValue("start", OffsetDateTime.ofInstant(start, ZoneOffset.UTC))
                .addValue("end", OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
    }

    /** @param period thời điểm bắt đầu kỳ theo giờ địa phương */
    public record PeriodRow(LocalDateTime period, long revenue, long orders, long cancelled) {
    }

    public record TopProductRow(Long productId, String productName, String productSlug, String imageUrl,
                                long quantity, long revenue) {
    }
}
