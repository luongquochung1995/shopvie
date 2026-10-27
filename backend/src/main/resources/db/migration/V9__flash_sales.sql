-- V9: flash sale (Phase 5). Quota bán đang chạy nằm trên Redis (trừ bằng Lua); bảng dưới là nguồn sự thật
-- để nạp lại Redis và đối soát. sold được cộng khi đơn flash sale được tạo (consumer Kafka).

CREATE TABLE flash_sales (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    start_at   TIMESTAMPTZ  NOT NULL,
    end_at     TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_flash_sales_period CHECK (end_at > start_at)
);
CREATE INDEX idx_flash_sales_period ON flash_sales (end_at, start_at);

CREATE TABLE flash_sale_items (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    flash_sale_id  BIGINT      NOT NULL REFERENCES flash_sales (id) ON DELETE CASCADE,
    variant_id     BIGINT      NOT NULL REFERENCES product_variants (id),
    product_id     BIGINT      NOT NULL REFERENCES products (id),
    sale_price     BIGINT      NOT NULL CHECK (sale_price > 0),
    quota          INT         NOT NULL CHECK (quota > 0),
    sold           INT         NOT NULL DEFAULT 0 CHECK (sold >= 0 AND sold <= quota),
    per_user_limit INT         NOT NULL DEFAULT 1 CHECK (per_user_limit > 0),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_flash_sale_items_variant UNIQUE (flash_sale_id, variant_id)
);

-- Lượt giữ hàng flash sale: tạo khi giữ quota thành công trên Redis, chuyển CONFIRMED (có đơn) / FAILED (đã trả quota)
CREATE TABLE flash_sale_reservations (
    id             UUID        PRIMARY KEY,
    item_id        BIGINT      NOT NULL REFERENCES flash_sale_items (id),
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    quantity       INT         NOT NULL CHECK (quantity > 0),
    status         VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'FAILED')),
    order_code     VARCHAR(36),
    failure_reason VARCHAR(50),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_flash_sale_reservations_user ON flash_sale_reservations (user_id, created_at DESC);
