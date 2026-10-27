-- V6: thanh toán online & vận chuyển (Phase 4).

-- ===== Payment =====
-- Mỗi lần người dùng bấm thanh toán cho một checkout là một bản ghi (thanh toán lại = bản ghi mới).
CREATE TABLE payments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    checkout_id      UUID         NOT NULL,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('VNPAY', 'MOMO')),
    -- Mã giao dịch gửi sang cổng (vnp_TxnRef / MoMo orderId); IPN gọi lặp được nhận diện qua mã này
    idempotency_key  VARCHAR(64)  NOT NULL UNIQUE,
    provider_txn_id  VARCHAR(64),
    amount           BIGINT       NOT NULL CHECK (amount > 0),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED')),
    expires_at       TIMESTAMPTZ  NOT NULL,
    paid_at          TIMESTAMPTZ,
    raw_payload      JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_checkout_id ON payments (checkout_id, id DESC);

-- Job hủy đơn online chưa thanh toán quá hạn
CREATE INDEX idx_orders_unpaid_online ON orders (created_at)
    WHERE status = 'PENDING' AND payment_status = 'UNPAID' AND payment_method <> 'COD';

-- ===== Shipping =====
-- Địa chỉ lấy hàng của shop (nơi đơn vị vận chuyển đến lấy hàng, dùng để tính phí ship)
ALTER TABLE shops
    ADD COLUMN pickup_name          VARCHAR(100),
    ADD COLUMN pickup_phone         VARCHAR(20),
    ADD COLUMN pickup_province_code VARCHAR(10),
    ADD COLUMN pickup_ward_code     VARCHAR(10),
    ADD COLUMN pickup_detail        VARCHAR(255);

-- Vận đơn của một đơn hàng (tạo khi seller xác nhận đơn)
CREATE TABLE shipments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code           VARCHAR(36)  NOT NULL UNIQUE REFERENCES orders (code),
    provider             VARCHAR(20)  NOT NULL CHECK (provider IN ('GHN', 'INTERNAL')),
    tracking_code        VARCHAR(64)  NOT NULL UNIQUE,
    status               VARCHAR(20)  NOT NULL
        CHECK (status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED')),
    fee                  BIGINT       CHECK (fee >= 0),
    expected_delivery_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Hành trình vận chuyển (timeline)
CREATE TABLE shipment_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shipment_id  BIGINT       NOT NULL REFERENCES shipments (id) ON DELETE CASCADE,
    status       VARCHAR(20)  NOT NULL,
    -- Trạng thái gốc của hãng (vd. GHN "delivering"), NULL với vận đơn nội bộ
    raw_status   VARCHAR(50),
    description  VARCHAR(255),
    occurred_at  TIMESTAMPTZ  NOT NULL,
    raw_payload  JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_shipment_events_shipment ON shipment_events (shipment_id, occurred_at);
