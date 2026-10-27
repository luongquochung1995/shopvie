# Rà soát bảo mật (P7-4) — OWASP Top 10 (2021)

Phạm vi: backend Spring Boot (`backend/`), frontend Angular SSR (`frontend/`), cấu hình triển khai (`deploy/`).
Kết luận: **không còn lỗi mức High đã biết**. Các điểm còn tồn (mức Medium/Low) ghi ở cuối, kèm hướng xử lý.

## Các mục kiểm tra theo plan

### XSS trong mô tả sản phẩm / đánh giá
- Mọi nội dung người dùng nhập (tên, mô tả sản phẩm, đánh giá, phản hồi của shop, tin nhắn chat, tên shop) được hiển thị
  bằng interpolation `{{ }}` của Angular (tự escape); xuống dòng giữ bằng CSS `whitespace-pre-line`.
  Không có `[innerHTML]`, `bypassSecurityTrust*` trong `frontend/src/app` (đã grep).
- Trường `highlightedName` của `/search` (có thẻ `<mark>`) được backend escape trước khi chèn thẻ; FE hiện không render
  trường này dưới dạng HTML.
- JSON-LD ở trang sản phẩm: `Seo.setJsonLd` thay `<` bằng `<` để dữ liệu không đóng sớm thẻ `<script>`.
- URL ảnh sản phẩm / banner chỉ nhận `http(s)` (chặn `javascript:` / `data:`).
- Ảnh upload kiểm tra magic bytes (jpg/png/webp), ≤ 5MB; không nhận SVG.

### IDOR / kiểm soát truy cập (A01)
- Phân quyền theo đường dẫn ở `SecurityConfig`: `/api/v1/seller/**` cần SELLER, `/api/v1/admin/**` cần ADMIN, endpoint
  công khai chỉ là `GET` sản phẩm/danh mục/shop/banner/SEO.
- Quyền sở hữu: tài nguyên của người mua luôn truy vấn kèm `userId` (giỏ hàng, địa chỉ, đơn hàng, đánh giá, thông báo);
  tài nguyên của shop đi qua `ShopApi.requireShopIdOfOwner` / `requireActiveShopIdOfOwner`. Không tìm thấy hoặc không
  phải của mình → 404 (không lộ sự tồn tại). Có integration test IDOR giữa 2 seller (`SellerCenterIT`) và cho địa chỉ.
- WebSocket: chỉ cho SUBSCRIBE `/user/queue/**` (hàng đợi riêng của user), không nhận SEND.

### CSRF cho cookie refresh
- API dùng Bearer token trong header → không bị CSRF; Spring CSRF tắt có chủ đích.
- Cookie refresh token: `HttpOnly`, `SameSite=Strict`, `Secure` ở prod, chỉ gửi tới `/api/v1/auth`.
- Thêm lớp thứ hai `RefreshOriginFilter`: `POST /auth/refresh` và `/auth/logout` có header `Origin` không thuộc danh sách
  CORS → 403 (test: `PlatformIT.refreshFromForeignOriginIsRejected`).

## Các nhóm OWASP khác

| Nhóm | Hiện trạng |
|---|---|
| A02 Cryptographic Failures | BCrypt cho mật khẩu; refresh token chỉ lưu SHA-256; JWT HS256 với secret từ biến môi trường; HSTS + TLS 1.2/1.3 ở nginx |
| A03 Injection | JPA/tham số hóa; sort theo whitelist; truy vấn Elasticsearch dựng bằng client (không ghép chuỗi) |
| A04 Insecure Design | Trừ kho / lượt voucher / suất flash sale nguyên tử; giá và tổng tiền luôn tính ở server (`expectedTotal` → 409) |
| A05 Misconfiguration | Prod: tắt Swagger, `show-details: never`, actuator ở cổng 8081 nội bộ, nginx trả 404 cho `/actuator/`; header bảo mật ở API (CSP `frame-ancestors 'none'`, `no-referrer`, `nosniff`) và SSR server |
| A07 Auth Failures | Rate limit đăng nhập/đăng ký/refresh theo IP (Bucket4j + Redis); phát hiện dùng lại refresh token → thu hồi mọi phiên; khóa tài khoản thu hồi refresh token ngay |
| A08 Integrity | IPN VNPay/MoMo kiểm chữ ký HMAC + số tiền + idempotency; webhook GHN cần token |
| A09 Logging | Log JSON (ECS) có `requestId`; `X-Request-Id` đầu vào chỉ nhận ký tự an toàn (chống log injection) |
| A10 SSRF | Server không tải URL do người dùng cung cấp (URL ảnh chỉ lưu, không fetch); SSR chỉ render cho host trong danh sách (`SITE_URL` / `NG_ALLOWED_HOSTS`), chỉ tin `X-Forwarded-Proto/For` |

## Còn tồn (không phải High)

| Mức | Vấn đề | Hướng xử lý |
|---|---|---|
| Medium | CSP của SSR server chưa giới hạn `script-src` (Angular SSR chèn script inline cho event replay) | Bật `security.autoCsp` trong `angular.json` rồi thêm `script-src` |
| Medium | Access token của tài khoản vừa bị khóa còn hiệu lực tối đa 15 phút; WebSocket đang mở không bị ngắt | Danh sách chặn ngắn hạn trên Redis theo `sub`; ngắt phiên STOMP khi nhận `UserLocked` |
| Low | Chưa có OTP nên quy tắc rate limit OTP chưa áp dụng | Thêm `@RateLimit("otp")` khi làm đăng nhập SĐT (Backlog) |
| Low | `/actuator/prometheus` không cần đăng nhập | Chỉ mở ở cổng quản trị nội bộ (prod); giữ nguyên |
| Low | Chưa quét phụ thuộc tự động | Thêm OWASP Dependency-Check / `npm audit` vào CI |
