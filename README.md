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

### Thanh toán online & vận chuyển (Phase 4)

- **Dev mặc định:** VNPay/MoMo chạy qua **cổng giả lập** của ShopVie (`/payment/sandbox`): chọn "Thanh toán thành công" hoặc "Hủy giao dịch",
  backend ký callback bằng key dev và xử lý đúng luồng return URL + kiểm tra chữ ký như cổng thật. Không trừ tiền thật.
- **Sandbox thật:** điền `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` và/hoặc `MOMO_*` trong `.env`, đặt `PAYMENT_SANDBOX_GATEWAY=false`.
  IPN cần `PAYMENT_PUBLIC_BASE_URL` truy cập được từ Internet (vd. ngrok); khi chạy local, return URL cũng xác nhận giao dịch.
- Đơn thanh toán online chưa trả tiền sau 15 phút bị hủy và hoàn kho (job chạy mỗi phút).
- **Vận chuyển:** chưa cấu hình GHN thì dùng bảng phí nội bộ theo vùng (cùng tỉnh / cùng miền / khác miền) và mã vận đơn `SVX…`.
  Có `GHN_TOKEN` + `GHN_SHOP_ID` thì gọi GHN (địa giới 2 cấp — chưa kiểm chứng trên sandbox). Webhook GHN:
  `POST /api/v1/shipping/ghn/webhook?token=<GHN_WEBHOOK_TOKEN>`.
- Seller khai báo **địa chỉ lấy hàng** ở Kênh người bán → Hồ sơ shop (dùng tính phí ship và tạo vận đơn).

### Tìm kiếm, voucher, flash sale (Phase 5)

Cần thêm **Elasticsearch 8** (`:9200`) và **Kafka** (`:9092`); flash sale cần **Redis có Lua** (`:6379`). Với Docker: `docker compose up -d`.
Không có Docker thì chạy bản portable (cách máy dev đang dùng):

- Elasticsearch 8.18.8 (zip): thêm vào `config/elasticsearch.yml`: `discovery.type: single-node`, `network.host: 127.0.0.1`,
  `xpack.security.enabled: false`, `xpack.ml.enabled: false`; chạy `bin\elasticsearch.bat` (`ES_JAVA_OPTS=-Xms1g -Xmx1g`).
- Kafka 3.9.2 (KRaft): format storage một lần bằng `kafka.tools.StorageTool`, chạy
  `java -cp "libs/*" kafka.Kafka config/kraft/server.properties` (file `.bat` lỗi "input line too long" trên Windows).
- Redis: Microsoft Garnet (cần .NET 10 runtime) — `GarnetServer.exe --bind 127.0.0.1 --port 6379 --lua --lua-transaction-mode --auth Password --password <REDIS_PASSWORD>`.

Ghi chú:
- Chỉ mục `products` tự tạo khi khởi động nếu chưa có; nạp lại toàn bộ: `POST /api/v1/admin/search/reindex` (admin). Sản phẩm thay đổi
  được đẩy qua Kafka (`shopvi.product-changed`) và cập nhật chỉ mục sau vài trăm ms. Thiếu ES → `/search` trả `503 SEARCH_UNAVAILABLE`.
- Flash sale: tạo phiên ở Quản trị → Flash sale (nhập id phân loại/SKU). Mua → đơn COD được tạo bất đồng bộ qua Kafka (`shopvi.flash-sale-orders`).
  Load test: `k6 run -e BASE_URL=http://localhost:8080 -e ITEM_ID=<id> -e QUOTA=<quota> loadtest/flash-sale.js` (cần `tokens.json`, xem đầu file).
- Mock API có sẵn voucher `SHOPVIE20K`, `FREESHIP30`, `SHOP10` và một phiên flash sale đang chạy.

### Thông báo realtime, chat, quản trị (Phase 6)

- WebSocket: STOMP over SockJS tại `/ws` (FE dev kết nối `http://localhost:8080/ws`). Client gửi `Authorization: Bearer <access token>`
  trong khung CONNECT và chỉ đăng ký được `/user/queue/notifications`, `/user/queue/chat`; gửi tin chat qua REST `/api/v1/chat/**`.
  Origin của FE phải nằm trong `CORS_ALLOWED_ORIGINS` (mặc định `http://localhost:4200`).
- Broker STOMP trong bộ nhớ — chỉ đúng khi chạy **một** instance backend.
- Quản trị: đăng nhập `admin@shopvi.local` → menu tài khoản → **Quản trị** (tổng quan, shop, sản phẩm, danh mục, tài khoản, voucher sàn, flash sale, banner).
- Chế độ mock (`npm run start:mock`): thông báo/chat realtime chạy giữa các tab cùng trình duyệt — mở một tab người mua và một tab seller để thử.
- `.claude/launch.json` có sẵn cấu hình `backend-dev` (jar + `.env`) và `frontend-dev` (cổng 4201, cần thêm vào `CORS_ALLOWED_ORIGINS`).

### Tối ưu & triển khai (Phase 7)

- **SEO:** trang sản phẩm/danh mục/trang chủ render SSR kèm title, description, canonical, Open Graph, Twitter card, JSON-LD
  (`core/seo/seo.service.ts`); trang không tồn tại trả HTTP 404. SSR server phục vụ `/robots.txt` và `/sitemap.xml`
  (dữ liệu từ `GET /api/v1/seo/sitemap`, cache 10 phút). Biến môi trường SSR: `API_ORIGIN` (backend nội bộ), `SITE_URL` (URL công khai — host của nó cùng `localhost` là các host
  được SSR; host khác rơi về render phía client). Nhiều tên miền thì đặt `NG_ALLOWED_HOSTS=a.com,b.com`.
- **Cache Redis:** cây danh mục (1 giờ), chi tiết sản phẩm (10 phút, xóa khi sản phẩm đổi), trang danh sách (1 phút).
  Khóa có tiền tố `CACHE_KEY_PREFIX` (mặc định `shopvi:`).
- **Rate limit** (Bucket4j + Redis, `app.rate-limit.rules`): đăng nhập, đăng ký, refresh theo IP; checkout, chat, đánh giá theo user.
  Vượt hạn mức → `429 TOO_MANY_REQUESTS` + `Retry-After`. Tắt bằng `RATE_LIMIT_ENABLED=false`.
- **Bảo mật:** kết quả rà soát OWASP Top 10 ở [docs/security-review.md](docs/security-review.md).
- **Load test:** `k6 run -e BASE_URL=http://localhost:8080 loadtest/read-apis.js` (500 RPS, ngưỡng p95 < 200ms) và `loadtest/checkout.js`;
  không có k6 thì `node loadtest/node-bench.mjs --base http://localhost:8080 --rps 500 --seconds 30`. Migration **V11** thêm index cho trang danh sách.
- **Giám sát:** `/actuator/prometheus` (histogram độ trễ HTTP); mỗi request có `X-Request-Id` (nằm trong mọi dòng log).
  Profile `prod` ghi log JSON (ECS). Prometheus + Grafana + cảnh báo trong `deploy/monitoring/`.
- **Production:** `backend/Dockerfile`, `frontend/Dockerfile` (multi-stage), `deploy/docker-compose.prod.yml` (nginx TLS + backend + SSR +
  Postgres/Redis/ES/Kafka + Prometheus/Grafana), `deploy/nginx.conf`. Ảnh lưu S3 / Cloudflare R2 (cấu hình `app.media` trong
  `application-prod.yml`), phục vụ qua CDN.

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

Từ Phase 5, IT cần thêm Redis (có Lua), Kafka và Elasticsearch đang chạy: bỏ hai cờ cuối, thêm `-Dspring.data.redis.password=...`
(chỉ mục test tên `it-products`, không đụng chỉ mục dev). Không chạy IT cùng lúc với backend dev trên cùng Kafka (chung tên topic).

## Ghi chú

- Giao diện đa ngôn ngữ vi/en (Transloco). Chuỗi hiển thị nằm trong `frontend/src/i18n/vi.json` và `en.json`; thêm key mới vào **cả hai** file (`npm test` kiểm tra hai file khớp key và tham số).
- Địa chỉ dùng địa giới hành chính 2 cấp (tỉnh/thành → xã/phường) áp dụng từ 01/07/2025.
  Dữ liệu: [provinces.open-api.vn](https://provinces.open-api.vn) (API v2), đóng gói tại `frontend/public/data/vn-divisions.json`.
- Ảnh sản phẩm trong seed dev lấy từ picsum.photos (ảnh minh họa).
