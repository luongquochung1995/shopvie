-- V11: index cho các trang danh sách sản phẩm (P7-5, đo bằng EXPLAIN ANALYZE trên ~96k sản phẩm).
-- Trước: mọi trang danh sách quét toàn bảng + sắp xếp (40-140 ms); sau: quét index theo đúng thứ tự, dừng ở LIMIT.

-- Mới nhất (trang chủ, danh mục) và danh sách admin (mọi trạng thái)
CREATE INDEX idx_products_created ON products (created_at DESC, id DESC) WHERE deleted_at IS NULL;
-- Bán chạy
CREATE INDEX idx_products_active_sold ON products (sold_count DESC, id DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
-- Theo giá
CREATE INDEX idx_products_active_price ON products (min_price, id DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
-- Danh mục + bán chạy (lọc danh mục là kiểu xem phổ biến nhất)
CREATE INDEX idx_products_active_category_sold ON products (category_id, sold_count DESC, id DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
-- Sitemap: sản phẩm đang bán, mới cập nhật trước
CREATE INDEX idx_products_active_updated ON products (updated_at DESC)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
