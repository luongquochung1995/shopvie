-- V1: schema cốt lõi cho User/Auth và Shop/Catalog (PLAN.md mục 6)
-- Quy ước: bảng snake_case số nhiều, id BIGINT IDENTITY, created_at/updated_at TIMESTAMPTZ, tiền tệ BIGINT (VND).

-- ===== User & Auth =====
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(20) UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500),
    role          VARCHAR(20)  NOT NULL DEFAULT 'BUYER' CHECK (role IN ('BUYER', 'SELLER', 'ADMIN')),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE addresses (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_name VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    province_code VARCHAR(10)  NOT NULL,
    district_code VARCHAR(10)  NOT NULL,
    ward_code     VARCHAR(10)  NOT NULL,
    detail        VARCHAR(255) NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user_id ON addresses (user_id);
-- Mỗi user chỉ có tối đa 1 địa chỉ mặc định
CREATE UNIQUE INDEX uq_addresses_user_default ON addresses (user_id) WHERE is_default;

-- ===== Shop & Catalog =====
CREATE TABLE shops (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    logo_url    VARCHAR(500),
    description TEXT,
    rating_avg  NUMERIC(2, 1) NOT NULL DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'BANNED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE TABLE categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id  BIGINT REFERENCES categories (id),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL UNIQUE,
    level      SMALLINT     NOT NULL DEFAULT 0 CHECK (level >= 0),
    position   INT          NOT NULL DEFAULT 0,
    icon_url   VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_categories_parent_id ON categories (parent_id);

CREATE TABLE products (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_id      BIGINT       NOT NULL REFERENCES shops (id),
    category_id  BIGINT       NOT NULL REFERENCES categories (id),
    name         VARCHAR(255) NOT NULL,
    -- slug unique toàn sàn vì trang chi tiết dùng GET /products/{slug}
    slug         VARCHAR(300) NOT NULL UNIQUE,
    description  TEXT,
    attributes   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    min_price    BIGINT       NOT NULL DEFAULT 0 CHECK (min_price >= 0),
    max_price    BIGINT       NOT NULL DEFAULT 0 CHECK (max_price >= min_price),
    sold_count   INT          NOT NULL DEFAULT 0 CHECK (sold_count >= 0),
    rating_avg   NUMERIC(2, 1) NOT NULL DEFAULT 0,
    rating_count INT          NOT NULL DEFAULT 0 CHECK (rating_count >= 0),
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING', 'ACTIVE', 'HIDDEN', 'BANNED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_products_shop_id ON products (shop_id);
CREATE INDEX idx_products_category_status ON products (category_id, status);
CREATE INDEX idx_products_attributes ON products USING GIN (attributes);

CREATE TABLE product_images (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);

CREATE TABLE product_variants (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku           VARCHAR(64)  NOT NULL UNIQUE,
    option_values JSONB        NOT NULL DEFAULT '{}'::jsonb,
    price         BIGINT       NOT NULL CHECK (price >= 0),
    stock         INT          NOT NULL DEFAULT 0 CHECK (stock >= 0),
    image_url     VARCHAR(500),
    version       INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
