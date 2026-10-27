-- V8: voucher sàn & voucher shop (Phase 5).

CREATE TABLE vouchers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- NULL = voucher sàn (admin tạo), có giá trị = voucher của shop
    shop_id         BIGINT       REFERENCES shops (id),
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('FIXED', 'PERCENT', 'FREESHIP')),
    -- FIXED: số tiền giảm (VND) · PERCENT: phần trăm (1-100) · FREESHIP: mức giảm phí ship tối đa (VND)
    value           BIGINT       NOT NULL CHECK (value > 0),
    min_order_value BIGINT       NOT NULL DEFAULT 0 CHECK (min_order_value >= 0),
    -- Giảm tối đa cho voucher PERCENT (NULL = không giới hạn)
    max_discount    BIGINT       CHECK (max_discount > 0),
    usage_limit     INT          NOT NULL CHECK (usage_limit > 0),
    used_count      INT          NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= usage_limit),
    per_user_limit  INT          NOT NULL DEFAULT 1 CHECK (per_user_limit > 0),
    start_at        TIMESTAMPTZ  NOT NULL,
    end_at          TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_vouchers_period CHECK (end_at > start_at),
    CONSTRAINT ck_vouchers_percent CHECK (type <> 'PERCENT' OR value <= 100)
);
-- Mã voucher duy nhất toàn sàn (không phân biệt hoa thường)
CREATE UNIQUE INDEX uq_vouchers_code ON vouchers (upper(code));
CREATE INDEX idx_vouchers_shop_period ON vouchers (shop_id, end_at);

-- Lượt dùng: voucher sàn áp cho cả checkout nên có 1 dòng cho mỗi đơn được giảm (cùng checkout_id)
CREATE TABLE voucher_usages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    voucher_id  BIGINT      NOT NULL REFERENCES vouchers (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    order_id    BIGINT      NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    checkout_id UUID        NOT NULL,
    amount      BIGINT      NOT NULL CHECK (amount >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_voucher_usages_order UNIQUE (voucher_id, order_id)
);
CREATE INDEX idx_voucher_usages_user ON voucher_usages (voucher_id, user_id);
CREATE INDEX idx_voucher_usages_order ON voucher_usages (order_id);

-- Snapshot voucher đã áp vào đơn: [{"code","type","scope","amount"}]
ALTER TABLE orders ADD COLUMN vouchers JSONB NOT NULL DEFAULT '[]'::jsonb;
