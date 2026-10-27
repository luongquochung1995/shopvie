-- V5: Seller Center (Phase 3).

-- Shop bị admin từ chối: seller sửa thông tin rồi gửi duyệt lại (REJECTED → PENDING).
ALTER TABLE shops DROP CONSTRAINT shops_status_check;
ALTER TABLE shops ADD CONSTRAINT shops_status_check
    CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'BANNED'));
ALTER TABLE shops ADD COLUMN reject_reason VARCHAR(500);
CREATE INDEX idx_shops_status_created ON shops (status, created_at);

-- Danh sách đơn của shop (mới nhất trước) và thống kê doanh số theo ngày/tháng.
CREATE INDEX idx_orders_shop_created ON orders (shop_id, created_at DESC);
