-- V10: thông báo realtime, chat buyer ↔ shop, banner trang chủ, kiểm duyệt (khóa user/shop/sản phẩm) — Phase 6.

-- Thông báo lưu theo loại + tham số; FE dịch nội dung theo ngôn ngữ người dùng
CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    params     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Mỗi cặp (người mua, shop) một hội thoại
CREATE TABLE conversations (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    buyer_id                   BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shop_id                    BIGINT       NOT NULL REFERENCES shops (id),
    last_message_preview       VARCHAR(200),
    last_message_at            TIMESTAMPTZ,
    buyer_unread               INT          NOT NULL DEFAULT 0 CHECK (buyer_unread >= 0),
    shop_unread                INT          NOT NULL DEFAULT 0 CHECK (shop_unread >= 0),
    -- Tin cuối cùng mỗi bên đã đọc (hiển thị "Đã xem" cho bên kia)
    buyer_last_read_message_id BIGINT,
    shop_last_read_message_id  BIGINT,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversations_buyer_shop UNIQUE (buyer_id, shop_id)
);
CREATE INDEX idx_conversations_buyer ON conversations (buyer_id, last_message_at DESC NULLS LAST);
CREATE INDEX idx_conversations_shop ON conversations (shop_id, last_message_at DESC NULLS LAST);

CREATE TABLE messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       BIGINT        NOT NULL REFERENCES users (id),
    sender_side     VARCHAR(10)   NOT NULL CHECK (sender_side IN ('BUYER', 'SHOP')),
    content         VARCHAR(2000) NOT NULL CHECK (length(content) > 0),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation ON messages (conversation_id, id DESC);

CREATE TABLE banners (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title      VARCHAR(100) NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    link_url   VARCHAR(500),
    position   INT          NOT NULL DEFAULT 0,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at  TIMESTAMPTZ,
    ends_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_banners_period CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX idx_banners_active ON banners (position, id) WHERE active;

-- Lý do khóa (admin hậu kiểm)
ALTER TABLE users ADD COLUMN lock_reason VARCHAR(500);
ALTER TABLE shops ADD COLUMN ban_reason VARCHAR(500);
ALTER TABLE products ADD COLUMN ban_reason VARCHAR(500);
CREATE INDEX idx_users_created ON users (created_at DESC);
CREATE INDEX idx_shops_status ON shops (status) WHERE status = 'BANNED';
