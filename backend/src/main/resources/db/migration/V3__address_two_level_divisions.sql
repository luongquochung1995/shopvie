-- V3: địa giới hành chính 2 cấp (tỉnh/thành → xã/phường) áp dụng từ 01/07/2025, không còn cấp huyện.
-- Giữ cột district_code (nullable) cho địa chỉ cũ / hãng vận chuyển còn dùng mã huyện.
ALTER TABLE addresses ALTER COLUMN district_code DROP NOT NULL;
