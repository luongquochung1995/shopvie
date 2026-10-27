-- V7: event publication registry (Spring Modulith) và đánh giá sản phẩm (Phase 5).

-- Sự kiện giữa các module được lưu lại tới khi listener xử lý xong (không mất khi lỗi/khởi động lại);
-- cấu trúc theo entity JPA của spring-modulith-events-jpa 1.4.
CREATE TABLE event_publication (
    id               UUID         NOT NULL PRIMARY KEY,
    listener_id      TEXT         NOT NULL,
    event_type       TEXT         NOT NULL,
    serialized_event TEXT         NOT NULL,
    publication_date TIMESTAMPTZ  NOT NULL,
    completion_date  TIMESTAMPTZ
);
CREATE INDEX idx_event_publication_incomplete ON event_publication (publication_date) WHERE completion_date IS NULL;
CREATE INDEX idx_event_publication_completion ON event_publication (completion_date);

-- Mỗi dòng đơn hàng đã hoàn thành được đánh giá đúng 1 lần
CREATE TABLE reviews (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_item_id     BIGINT        NOT NULL UNIQUE REFERENCES order_items (id) ON DELETE CASCADE,
    user_id           BIGINT        NOT NULL REFERENCES users (id),
    product_id        BIGINT        NOT NULL REFERENCES products (id),
    shop_id           BIGINT        NOT NULL REFERENCES shops (id),
    -- Snapshot tên sản phẩm và phân loại đã mua, vd. "Đỏ, M"
    product_name      VARCHAR(255)  NOT NULL,
    variant_name      VARCHAR(255),
    rating            SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content           VARCHAR(1000),
    -- Danh sách URL ảnh (tối đa 5)
    media             JSONB         NOT NULL DEFAULT '[]'::jsonb,
    seller_reply      VARCHAR(1000),
    seller_replied_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_product_created ON reviews (product_id, created_at DESC);
CREATE INDEX idx_reviews_shop_created ON reviews (shop_id, created_at DESC);
