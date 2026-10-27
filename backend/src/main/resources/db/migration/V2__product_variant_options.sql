-- V2: định nghĩa nhóm phân loại của sản phẩm theo đúng thứ tự hiển thị, vd:
-- [{"name":"Màu sắc","values":["Đỏ","Xanh"]},{"name":"Kích cỡ","values":["M","L"]}]
-- Cần cột riêng vì JSONB object (product_variants.option_values) không giữ thứ tự key.
ALTER TABLE products ADD COLUMN variant_options JSONB NOT NULL DEFAULT '[]'::jsonb;
