-- V4: giỏ hàng và đơn hàng (Phase 2). Đơn hàng lưu snapshot giá/tên/địa chỉ, không join ngược để hiển thị.

CREATE TABLE cart_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    variant_id BIGINT      NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL CHECK (quantity > 0),
    selected   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_items_user_variant UNIQUE (user_id, variant_id)
);

CREATE TABLE orders (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Mã đơn public-facing dạng UUIDv7 (sắp theo thời gian, không đoán được id nội bộ)
    code             VARCHAR(36) NOT NULL UNIQUE,
    user_id          BIGINT      NOT NULL REFERENCES users (id),
    shop_id          BIGINT      NOT NULL REFERENCES shops (id),
    status           VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'RETURNED')),
    subtotal         BIGINT      NOT NULL CHECK (subtotal >= 0),
    shipping_fee     BIGINT      NOT NULL CHECK (shipping_fee >= 0),
    discount         BIGINT      NOT NULL DEFAULT 0 CHECK (discount >= 0),
    total            BIGINT      NOT NULL CHECK (total >= 0),
    payment_method   VARCHAR(20) NOT NULL CHECK (payment_method IN ('COD', 'VNPAY', 'MOMO')),
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    shipping_address JSONB       NOT NULL,
    note             VARCHAR(500),
    -- Các đơn tách theo shop trong cùng một lần checkout
    checkout_id      UUID        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_orders_total CHECK (total = subtotal + shipping_fee - discount)
);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_shop_status ON orders (shop_id, status);
CREATE INDEX idx_orders_checkout_id ON orders (checkout_id);

CREATE TABLE order_items (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- SKU có thể bị xóa sau này; snapshot bên dưới vẫn giữ để hiển thị lịch sử
    variant_id   BIGINT       REFERENCES product_variants (id) ON DELETE SET NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_slug VARCHAR(300) NOT NULL,
    variant_name VARCHAR(255),
    image_url    VARCHAR(500),
    unit_price   BIGINT       NOT NULL CHECK (unit_price >= 0),
    quantity     INT          NOT NULL CHECK (quantity > 0),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE order_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    note        VARCHAR(500),
    -- id user thực hiện (buyer/seller/admin); NULL = hệ thống
    changed_by  BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
