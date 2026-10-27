# ShopVi

Sàn thương mại điện tử đa người bán (tham khảo Shopee). Kế hoạch chi tiết: [PLAN.md](PLAN.md).

- **Backend:** Java 21, Spring Boot 3.5, Spring Modulith, PostgreSQL 17, Redis 7, MinIO — thư mục `backend/`
- **Frontend:** Angular 21 (SSR, standalone, Signals, NgRx SignalStore), Tailwind CSS 4, PrimeNG 21 — thư mục `frontend/`

## Yêu cầu

- JDK 21, Node.js 22, Docker (Docker Compose v2)

## Chạy môi trường dev

```bash
cp .env.example .env          # rồi sửa mật khẩu; JWT_SECRET tạo bằng: openssl rand -base64 48
docker compose up -d          # Postgres :5432, Redis :6379, MinIO :9000 (console :9001)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm start
```

| Thành phần | URL |
|---|---|
| Frontend | http://localhost:4200 |
| Backend API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |

### Chạy frontend không cần backend (mock)

```bash
cd frontend && npm run start:mock
```

Mọi request tới API được trả lời bởi backend giả trong `frontend/src/app/mock` (cùng format với API thật, có độ trễ ~250ms):
20 danh mục, 3 shop, 100 sản phẩm có phân loại; đăng nhập/đăng ký, hồ sơ, sổ địa chỉ, upload ảnh (lưu dạng data URL),
giỏ hàng/đặt hàng, Kênh người bán (mở shop, sản phẩm, đơn hàng, dashboard) và trang duyệt shop của admin.
Tài khoản giống seed dev (`buyer@shopvi.local` / `Password@123`...). Dữ liệu thay đổi (tài khoản, địa chỉ, giỏ, đơn, shop,
sản phẩm của seller, phiên đăng nhập) được lưu trong `localStorage` (key `shopvi.mock.*`) — xóa các key này để về dữ liệu ban đầu.
Code mock chỉ có trong cấu hình `mock`, không nằm trong bản build production.

### Dữ liệu seed (profile `dev`)

Khi DB trống, backend tự seed 20 danh mục, 3 shop và 100 sản phẩm có phân loại. Mọi tài khoản dùng mật khẩu `Password@123` (chỉ dành cho dev):

| Email | Vai trò |
|---|---|
| `admin@shopvi.local` | ADMIN |
| `buyer@shopvi.local` | BUYER |
| `seller1@shopvi.local` … `seller3@shopvi.local` | SELLER |

Kênh người bán: `/seller-center` (người mua bấm "Mở shop bán hàng" để đăng ký; shop chờ admin duyệt ở `/admin/shops`).

### Chạy khi chưa có Redis/MinIO

Cache tự bỏ qua lỗi Redis, nhưng nên tắt hẳn để tránh chờ timeout:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--spring.cache.type=simple --management.health.redis.enabled=false"
```

Upload ảnh (`/media/upload`) cần MinIO; thiếu MinIO API trả `503 STORAGE_UNAVAILABLE`.

## Test

```bash
cd backend && ./mvnw verify   # unit test + integration test (Testcontainers: Postgres, Redis, MinIO)
cd frontend && npm test       # Vitest
cd frontend && npm run e2e    # Playwright, chạy trên mock API (tự bật dev server cổng 4300)
```

E2E dùng trình duyệt Playwright (`npx playwright install chromium`) hoặc trình duyệt có sẵn: `PW_CHANNEL=msedge npm run e2e`.
Chạy e2e với backend thật: bật backend + `npm start` rồi `E2E_BASE_URL=http://localhost:4200 npm run e2e` (mỗi test tự đăng ký tài khoản mới).

Không có Docker thì integration test tự bỏ qua. Có thể chạy chúng trên một Postgres sẵn có (DB riêng, sẽ bị ghi dữ liệu):

```bash
./mvnw verify -Dit.external=true \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/shopvi_it \
  -Dspring.datasource.username=shopvi -Dspring.datasource.password=... \
  -Dspring.cache.type=simple -Dmanagement.health.redis.enabled=false
```

## Ghi chú

- Giao diện đa ngôn ngữ vi/en (Transloco). Chuỗi hiển thị nằm trong `frontend/src/i18n/vi.json` và `en.json`; thêm key mới vào **cả hai** file (`npm test` kiểm tra hai file khớp key và tham số).
- Địa chỉ dùng địa giới hành chính 2 cấp (tỉnh/thành → xã/phường) áp dụng từ 01/07/2025.
  Dữ liệu: [provinces.open-api.vn](https://provinces.open-api.vn) (API v2), đóng gói tại `frontend/public/data/vn-divisions.json`.
- Ảnh sản phẩm trong seed dev lấy từ picsum.photos (ảnh minh họa).
