# PLAN.md — ShopVi: sàn thương mại điện tử (tham khảo Shopee)

> **Dành cho Claude.** Đây là kế hoạch triển khai nguồn duy nhất (single source of truth) cho dự án.
> Đọc toàn bộ file này trước khi bắt đầu bất kỳ task nào. Làm theo đúng thứ tự Phase,
> tick `[x]` vào checkbox khi task hoàn thành và đạt tiêu chí nghiệm thu.

---

## 0. Quy tắc làm việc cho Claude

1. **Làm từng Phase một.** Không bắt đầu Phase N+1 khi Phase N chưa đạt "Definition of Done".
2. **Mỗi task = một thay đổi nhỏ, chạy được.** Sau mỗi task: build pass, test pass, rồi mới tick checkbox.
3. **Không tự ý đổi stack/công nghệ** đã chốt ở mục 2. Nếu thấy cần đổi, hỏi người dùng trước.
4. **Không tự thêm tính năng ngoài plan.** Ý tưởng mới ghi vào mục 11 "Backlog".
5. **Migration DB chỉ thêm mới, không sửa file Flyway đã chạy** (`V{n}__*.sql`). Muốn đổi schema → tạo file migration mới.
6. **Không commit secret.** Mọi key/password đặt trong `.env` (đã có trong `.gitignore`), có `.env.example` mẫu.
7. **Viết test cùng lúc với code:** service có unit test; repository/API có integration test bằng Testcontainers.
8. **Khi xong một Phase:** cập nhật mục 10 "Nhật ký tiến độ" (ngày, việc đã làm, vấn đề còn tồn).
9. Ngôn ngữ: code, tên biến, commit message bằng **tiếng Anh**; comment giải thích nghiệp vụ và tài liệu có thể dùng **tiếng Việt**.

---

## 1. Mục tiêu sản phẩm

**Tên dự án:** `ShopVi` — repo `shopvi`, Java package `com.shopvi`, Maven `groupId=com.shopvi`, `artifactId=shopvi-backend`, Angular project `shopvi-frontend`.

Sàn TMĐT đa người bán (marketplace) gồm 3 vai trò:

| Role | Mô tả |
|---|---|
| `BUYER` | Duyệt/tìm sản phẩm, giỏ hàng, đặt hàng, thanh toán, đánh giá, chat với shop |
| `SELLER` | Mở shop, quản lý sản phẩm/tồn kho, xử lý đơn, tạo voucher shop, xem doanh thu |
| `ADMIN` | Duyệt shop/sản phẩm, quản lý danh mục, banner, voucher sàn, flash sale, thống kê |

**MVP (Phase 0 → 2):** người mua xem sản phẩm, thêm giỏ, đặt hàng COD, xem lịch sử đơn.

---

## 2. Tech stack (đã chốt)

### Backend
- Java **21** (LTS), Spring Boot **3.5.x**, Maven
- Spring Web, Spring Data JPA (Hibernate), Spring Validation, Spring Security (JWT), Spring Modulith
- Flyway (migration), MapStruct, Lombok, Springdoc OpenAPI
- Bật Virtual Threads: `spring.threads.virtual.enabled=true`
- Test: JUnit 5, Mockito, Testcontainers (PostgreSQL, Redis)

### Frontend
- Angular **20+**, standalone components, **Signals**, control flow `@if/@for`
- Angular SSR (`--ssr`) cho SEO trang sản phẩm
- State: NgRx SignalStore
- UI: Tailwind CSS + PrimeNG
  - **Ghim Angular 21 + PrimeNG 21.x (MIT).** PrimeNG 22+ chuyển sang license thương mại (cần license key, không có key sẽ hiện badge "Invalid PrimeUI License"). Không nâng Angular/PrimeNG lên 22+ khi chưa hỏi người dùng.
- Đa ngôn ngữ: **Transloco** (vi mặc định, en), đổi ngôn ngữ runtime, lưu cookie `lang` để SSR render đúng ngôn ngữ. File dịch: `frontend/src/i18n/vi.json`, `en.json` — **không viết cứng chuỗi giao diện** trong component; thêm key mới phải thêm ở cả 2 file (có test kiểm tra).
- Test: Jest hoặc Vitest (unit), Playwright (e2e)

### Lưu trữ dữ liệu
| Vai trò | Công nghệ | Khi nào thêm |
|---|---|---|
| DB chính (nguồn sự thật) | **PostgreSQL 17** (JSONB cho thuộc tính sản phẩm) | Phase 0 |
| Cache, giỏ hàng, rate limit, flash sale | **Redis 7** | Phase 0 |
| Ảnh/video | **MinIO** (dev) → S3 / Cloudflare R2 (prod) | Phase 1 |
| Tìm kiếm | **Elasticsearch 8 / OpenSearch** | Phase 5 (trước đó dùng Postgres full-text) |
| Event bus | **Kafka** (KRaft mode) | Phase 5 |

### DevOps
- Docker Compose cho môi trường dev
- GitHub Actions: build + test cho BE và FE ở mỗi PR
- Prometheus + Grafana (Phase 7)

---

## 3. Kiến trúc

**Modular Monolith** bằng Spring Modulith — một ứng dụng Spring Boot, chia module theo nghiệp vụ.
Module **không được gọi trực tiếp repository/entity của module khác**; chỉ giao tiếp qua
public service interface hoặc application event. Mục tiêu: sau này tách microservice dễ dàng.

```
[Angular SSR] ──HTTPS──> [Spring Boot API :8080] ──> PostgreSQL :5432
                                   │                  Redis      :6379
                                   │                  MinIO      :9000 (console :9001)
                                   └── (Phase 5) ──>  Elasticsearch :9200, Kafka :9092
```

---

## 4. Cấu trúc repository

```
shopvi/
├─ PLAN.md                    # file này
├─ docker-compose.yml
├─ .env.example
├─ .github/workflows/ci.yml
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/shopvi/
│     │  ├─ ShopviApplication.java
│     │  ├─ common/           # config, security, exception, ApiResponse, PageResponse, util
│     │  ├─ auth/             # đăng ký, đăng nhập, JWT, refresh token
│     │  ├─ user/             # hồ sơ, địa chỉ
│     │  ├─ shop/             # shop của seller
│     │  ├─ catalog/          # category, product, variant, image
│     │  ├─ media/            # upload file lên MinIO/S3
│     │  ├─ cart/
│     │  ├─ order/
│     │  ├─ payment/
│     │  ├─ shipping/
│     │  ├─ promotion/        # voucher, flash sale
│     │  ├─ review/
│     │  ├─ search/
│     │  ├─ notification/     # thông báo, chat (WebSocket)
│     │  └─ admin/
│     │     # mỗi module: web/ (controller, dto) · application/ (service) · domain/ (entity, event) · infra/ (repository)
│     ├─ main/resources/
│     │  ├─ application.yml
│     │  ├─ application-dev.yml
│     │  └─ db/migration/     # V1__init.sql, V2__...
│     └─ test/java/com/shopvi/
└─ frontend/
   ├─ package.json
   └─ src/app/
      ├─ core/                # interceptors (auth, error), guards, auth.store, layout (header, footer)
      ├─ shared/              # ui components: product-card, rating-stars, price, pagination, empty-state
      ├─ features/
      │  ├─ home/
      │  ├─ product-list/     # theo category + bộ lọc
      │  ├─ product-detail/
      │  ├─ search/
      │  ├─ cart/
      │  ├─ checkout/
      │  ├─ orders/
      │  ├─ account/          # profile, addresses
      │  ├─ auth/             # login, register
      │  ├─ seller-center/    # lazy, guard role SELLER
      │  └─ admin/            # lazy, guard role ADMIN
      ├─ app.routes.ts
      └─ app.config.ts
```

---

## 5. Quy ước (conventions)

### API
- Base path: `/api/v1`
- JSON camelCase. Thời gian ISO-8601 UTC. Tiền tệ: `BIGINT` đơn vị **VND** (không dùng float).
- Response thành công:
  ```json
  { "success": true, "data": { ... }, "message": null }
  ```
- Response lỗi (xử lý tập trung ở `@RestControllerAdvice`):
  ```json
  { "success": false, "error": { "code": "PRODUCT_NOT_FOUND", "message": "...", "details": [] } }
  ```
- Phân trang: query `?page=0&size=20&sort=createdAt,desc` → `data: { items, page, size, totalItems, totalPages }`
- HTTP status đúng ngữ nghĩa: 200/201/204/400/401/403/404/409/422/429/500.
- Endpoint public: `GET /products/**`, `GET /categories/**`, `GET /shops/**`, `/auth/**`. Còn lại cần JWT.
- Endpoint seller: `/api/v1/seller/**` (role SELLER). Endpoint admin: `/api/v1/admin/**` (role ADMIN).

### Auth
- Access token JWT 15 phút (header `Authorization: Bearer`), refresh token 7 ngày (lưu DB, xoay vòng mỗi lần refresh, gửi qua httpOnly cookie).
- Mật khẩu hash bằng BCrypt.
- Kiểm tra **quyền sở hữu**: seller chỉ thao tác được trên shop/sản phẩm/đơn của mình.

### Database
- Tên bảng `snake_case` số nhiều; khóa chính `id BIGINT GENERATED ALWAYS AS IDENTITY` (hoặc UUIDv7 cho bảng public-facing như `orders.code`).
- Mọi bảng có `created_at`, `updated_at` (`TIMESTAMPTZ`).
- Soft delete bằng cột `deleted_at` cho `products`, `shops`.
- `ddl-auto: validate` — schema chỉ thay đổi qua Flyway.

### Frontend
- Chỉ dùng standalone component, `inject()` thay vì constructor injection, `signal/computed` cho state cục bộ.
- Gọi API qua service trong `core/api/`; base URL lấy từ `environment.ts`.
- Lazy-load mọi route trong `features/`.
- Responsive mobile-first (Shopee chủ yếu là mobile).

### Git
- Branch: `feat/<phase>-<short-name>`, `fix/...`. Commit theo Conventional Commits (`feat:`, `fix:`, `chore:`...).

---

## 6. Schema DB cốt lõi (V1__init.sql và các migration sau)

```sql
-- ===== User & Auth =====
users(id, email UNIQUE, phone UNIQUE, password_hash, full_name, avatar_url,
      role VARCHAR(20) /* BUYER|SELLER|ADMIN */, status /* ACTIVE|LOCKED */,
      created_at, updated_at)
refresh_tokens(id, user_id FK, token_hash UNIQUE, expires_at, revoked_at, created_at)
addresses(id, user_id FK, receiver_name, phone, province_code, district_code, ward_code,
          detail, is_default, created_at, updated_at)

-- ===== Shop & Catalog =====
shops(id, owner_id FK UNIQUE, name, slug UNIQUE, logo_url, description,
      rating_avg NUMERIC(2,1), status /* PENDING|ACTIVE|BANNED */, created_at, updated_at, deleted_at)
categories(id, parent_id FK NULL, name, slug UNIQUE, level, position, icon_url)
products(id, shop_id FK, category_id FK, name, slug, description,
         attributes JSONB,                   -- {"brand":"Apple","material":"Cotton"}
         min_price BIGINT, max_price BIGINT, sold_count INT, rating_avg NUMERIC(2,1), rating_count INT,
         status /* DRAFT|PENDING|ACTIVE|HIDDEN|BANNED */, created_at, updated_at, deleted_at)
product_images(id, product_id FK, url, position)
product_variants(id, product_id FK, sku UNIQUE, option_values JSONB, -- {"color":"Đỏ","size":"M"}
                 price BIGINT, stock INT CHECK (stock >= 0), image_url, version INT)

-- ===== Cart =====
cart_items(id, user_id FK, variant_id FK, quantity, selected BOOLEAN, created_at,
           UNIQUE(user_id, variant_id))

-- ===== Order =====
orders(id, code VARCHAR UNIQUE, user_id FK, shop_id FK,
       status /* PENDING|CONFIRMED|SHIPPING|DELIVERED|COMPLETED|CANCELLED|RETURNED */,
       subtotal BIGINT, shipping_fee BIGINT, discount BIGINT, total BIGINT,
       payment_method /* COD|VNPAY|MOMO */, payment_status /* UNPAID|PAID|REFUNDED */,
       shipping_address JSONB,               -- snapshot địa chỉ lúc đặt
       note, checkout_id,                    -- các đơn tách shop cùng 1 lần checkout
       created_at, updated_at)
order_items(id, order_id FK, variant_id FK, product_id, product_name, variant_name, image_url,
            unit_price BIGINT, quantity)    -- snapshot giá/tên lúc đặt
order_status_history(id, order_id FK, from_status, to_status, note, changed_by, created_at)

-- ===== Payment (Phase 4) =====
payments(id, checkout_id, provider, provider_txn_id, amount BIGINT,
         status /* PENDING|SUCCESS|FAILED */, idempotency_key UNIQUE, raw_payload JSONB, created_at)

-- ===== Promotion (Phase 5) =====
vouchers(id, shop_id FK NULL /* NULL = voucher sàn */, code, type /* FIXED|PERCENT|FREESHIP */,
         value, min_order_value, max_discount, usage_limit, used_count, per_user_limit,
         start_at, end_at, status)
voucher_usages(id, voucher_id FK, user_id FK, order_id FK, created_at)
flash_sales(id, name, start_at, end_at, status)
flash_sale_items(id, flash_sale_id FK, variant_id FK, sale_price BIGINT, quota INT, sold INT)

-- ===== Review (Phase 5) =====
reviews(id, order_item_id FK UNIQUE, user_id FK, product_id FK, rating SMALLINT CHECK (rating BETWEEN 1 AND 5),
        content, media JSONB, seller_reply, created_at)
```

**Index bắt buộc:** `products(shop_id)`, `products(category_id, status)`, GIN `products(attributes)`,
`orders(user_id, created_at DESC)`, `orders(shop_id, status)`, `product_variants(product_id)`.

**Quy tắc nghiệp vụ quan trọng:**
- Đơn hàng **luôn lưu snapshot** giá, tên, địa chỉ — không join ngược để hiển thị lịch sử.
- Trừ kho nguyên tử: `UPDATE product_variants SET stock = stock - :q, version = version + 1 WHERE id = :id AND stock >= :q` → nếu 0 row bị ảnh hưởng thì ném `OUT_OF_STOCK`.
- Hủy đơn → hoàn kho trong cùng transaction.

---

## 7. Môi trường dev

`docker-compose.yml` cần có các service: `postgres:17`, `redis:7`, `minio/minio`
(Phase 5 thêm `elasticsearch:8`, `kafka` KRaft). Dùng named volume, healthcheck cho từng service.

`.env.example`:
```
POSTGRES_DB=shopvi
POSTGRES_USER=shopvi
POSTGRES_PASSWORD=change_me
REDIS_PASSWORD=change_me
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=change_me_min8chars
JWT_SECRET=change_me_to_a_long_random_base64_string
```

Lệnh thường dùng:
```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd backend && ./mvnw verify
cd frontend && npm start
cd frontend && npm test
```

Cổng: FE `4200`, BE `8080`, Swagger UI `http://localhost:8080/swagger-ui.html`.

---

## 8. Lộ trình triển khai

### Phase 0 — Khởi tạo dự án
- [x] P0-1 Tạo cấu trúc repo như mục 4, `.gitignore`, `.env.example`, `README.md` ngắn
- [ ] P0-2 `docker-compose.yml` với Postgres, Redis, MinIO + healthcheck *(đã viết, chưa chạy thử — máy dev chưa có Docker)*
- [x] P0-3 Khởi tạo Spring Boot (Web, JPA, Security, Validation, Flyway, PostgreSQL, Redis, Actuator, Springdoc, Lombok, MapStruct, Modulith, Testcontainers)
- [x] P0-4 `common/`: `ApiResponse`, `PageResponse`, `GlobalExceptionHandler`, `BusinessException` + `ErrorCode` enum, CORS config cho `localhost:4200`
- [x] P0-5 Flyway `V1__init.sql` tạo bảng users, refresh_tokens, addresses, shops, categories, products, product_images, product_variants *(đã chạy trên PostgreSQL 17.11 local)*
- [x] P0-6 Khởi tạo Angular `ng new frontend --ssr --style=scss --routing`, cài Tailwind + PrimeNG, layout header/footer
- [ ] P0-7 GitHub Actions CI: BE `mvn verify`, FE `npm ci && npm run build && npm test` *(đã chạy 1 lần trên GitHub ở commit `4e1cffc`: **đỏ** — 2 job FE hỏng ở `npm ci` vì lockfile trỏ registry nội bộ (đã sửa), job BE hỏng ở `mvn verify` sau khi chạy IT bằng Testcontainers (chưa xem được log); action nâng lên v5)*

**DoD:** `docker compose up` + BE + FE chạy được; `GET /actuator/health` = UP; CI xanh.

### Phase 1 — Auth, User, Catalog
- [x] P1-1 Auth: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`; JWT filter; phân quyền theo role
- [x] P1-2 User: `GET/PUT /me`, CRUD `/me/addresses` (chỉ 1 địa chỉ default)
- [x] P1-3 Category: cây danh mục nhiều cấp, `GET /categories` (cache Redis), admin CRUD *(logic cache/evict đã test với cache in-memory; Redis thật chưa chạy — chờ Docker/CI; trang Quản trị → Danh mục bổ sung khi rà soát 2026-10-27)*
- [ ] P1-4 Media: `POST /media/upload` → MinIO, trả URL; giới hạn loại file (jpg/png/webp) và dung lượng (≤ 5MB) *(code + unit test xong, kiểm tra magic bytes/5MB/401 đã chạy thật; upload vào MinIO chờ `MediaUploadIT` chạy với Docker/CI)*
- [x] P1-5 Product (public): `GET /products` (lọc category, khoảng giá, sort mới nhất/bán chạy/giá), `GET /products/{slug}` kèm variants + images + shop info
- [x] P1-6 Seed data dev (`V2__seed_dev.sql` hoặc `CommandLineRunner` profile dev): 1 admin, 3 seller + shop, 20 category, 100 sản phẩm có variant
- [x] P1-7 FE: trang login/register, auth interceptor + tự refresh token, guard theo role
- [x] P1-8 FE: trang chủ (banner, danh mục, sản phẩm gợi ý), danh sách theo category + bộ lọc, chi tiết sản phẩm (gallery ảnh, chọn variant → đổi giá/tồn kho)
- [x] P1-9 FE: trang tài khoản + sổ địa chỉ (dữ liệu tỉnh/huyện/xã VN) *(đổi sang địa giới 2 cấp tỉnh → xã từ 07/2025, xem nhật ký)*

**DoD:** đăng ký/đăng nhập được; xem danh sách + chi tiết sản phẩm từ seed data; test auth và product API pass.

### Phase 2 — Giỏ hàng & Đặt hàng (MVP)
- [x] P2-1 Cart API: `GET /cart` (nhóm theo shop), `POST /cart/items`, `PATCH /cart/items/{id}` (quantity, selected), `DELETE`
- [x] P2-2 Checkout preview: `POST /checkout/preview` → tính tiền theo từng shop (phí ship cố định tạm thời)
- [x] P2-3 Place order: `POST /checkout` → tách đơn theo shop, trừ kho nguyên tử, xóa item đã mua khỏi giỏ — **tất cả trong 1 transaction**; chỉ COD
- [x] P2-4 Order API buyer: `GET /orders?status=`, `GET /orders/{code}`, `POST /orders/{code}/cancel` (chỉ khi PENDING/CONFIRMED, hoàn kho)
- [x] P2-5 State machine trạng thái đơn (enum + bảng chuyển trạng thái hợp lệ) + ghi `order_status_history`
- [x] P2-6 Integration test: 2 request đồng thời mua món cuối cùng → chỉ 1 thành công
- [x] P2-7 FE: icon giỏ hàng có badge, trang giỏ hàng (chọn theo shop/từng món), trang checkout (chọn địa chỉ, ghi chú), trang đặt hàng thành công
- [x] P2-8 FE: trang "Đơn mua" với các tab trạng thái, chi tiết đơn, hủy đơn

**DoD (MVP):** luồng end-to-end xem → thêm giỏ → checkout COD → xem đơn → hủy đơn hoạt động; không bán vượt tồn kho; có test e2e Playwright cho luồng mua hàng.

### Phase 3 — Seller Center
- [x] P3-1 `POST /seller/shop/register` (user BUYER → SELLER, shop PENDING), admin duyệt shop *(thêm trạng thái REJECTED + gửi duyệt lại, xem nhật ký)*
- [x] P3-2 Seller product CRUD + quản lý variants (sinh tổ hợp option, set giá/tồn kho từng SKU), ẩn/hiện sản phẩm
- [x] P3-3 Seller order: danh sách đơn của shop, xác nhận đơn, chuyển SHIPPING / DELIVERED
- [x] P3-4 Dashboard: doanh thu theo ngày/tháng, số đơn theo trạng thái, top sản phẩm bán chạy
- [x] P3-5 FE: module `seller-center` (lazy) gồm layout sidebar, các trang trên

**DoD:** seller tự tạo sản phẩm có variant, sản phẩm hiển thị phía buyer, xử lý đơn đến DELIVERED.

### Phase 4 — Thanh toán online & Vận chuyển
- [ ] P4-1 Interface `PaymentProvider` (strategy pattern) + implementation **VNPay sandbox** *(code + test xong, chạy được qua cổng giả lập; chưa chạy với sandbox thật — chưa có tài khoản)*
- [ ] P4-2 Tạo URL thanh toán, xử lý return URL + **IPN webhook**: verify checksum, idempotency, cập nhật `payment_status` *(code + test xong, chạy được qua cổng giả lập; chưa chạy với sandbox thật — chưa có tài khoản)*
- [x] P4-3 Job tự hủy đơn chưa thanh toán sau 15 phút + hoàn kho (`@Scheduled` hoặc Redis delayed queue)
- [ ] P4-4 Implementation **MoMo sandbox** *(code + test xong, chạy được qua cổng giả lập; chưa chạy với sandbox thật — chưa có tài khoản)*
- [ ] P4-5 Interface `ShippingProvider` + **GHN sandbox**: tính phí ship ở checkout preview, tạo vận đơn khi seller xác nhận, webhook cập nhật trạng thái giao *(GHN theo địa giới 2 cấp chưa kiểm chứng — tài liệu GHN không công khai, chưa có sandbox; đang chạy bằng bảng phí/vận đơn nội bộ dự phòng)*
- [x] P4-6 FE: chọn phương thức thanh toán, trang kết quả thanh toán, hiển thị phí ship thực tế, timeline vận chuyển

**DoD:** thanh toán VNPay sandbox thành công/thất bại đều xử lý đúng; IPN gọi lặp không xử lý 2 lần; phí ship lấy từ GHN.

### Phase 5 — Tìm kiếm, Khuyến mãi, Đánh giá
- [x] P5-1 Thêm Elasticsearch + Kafka vào docker-compose *(máy dev không có Docker: chạy bản portable, xem README)*
- [x] P5-2 Đồng bộ product → Elasticsearch qua event (`ProductChangedEvent` → Kafka → indexer); job reindex toàn bộ
- [x] P5-3 `GET /search?q=` với analyzer tiếng Việt (bỏ dấu), bộ lọc, sort, highlight; `GET /search/suggest?q=` autocomplete
- [x] P5-4 Voucher: sàn + shop, áp dụng ở checkout, kiểm tra điều kiện và giới hạn lượt dùng (chống race condition)
- [x] P5-5 Flash Sale: admin tạo phiên; tồn kho flash sale nằm trên Redis, trừ bằng **Lua script**; ghi DB bất đồng bộ qua Kafka; rate limit endpoint mua
- [x] P5-6 Review: chỉ đánh giá được order_item đã COMPLETED, 1 lần; upload ảnh; cập nhật `rating_avg`; seller trả lời *(tối đa 5 ảnh; form đánh giá ở FE tải ảnh lên từ rà soát 2026-10-27)*
- [x] P5-7 FE: thanh tìm kiếm + gợi ý, trang kết quả tìm kiếm, ô nhập voucher, khu Flash Sale có đồng hồ đếm ngược, phần đánh giá ở trang chi tiết

**DoD:** tìm kiếm "ao thun" ra kết quả "áo thun"; load test flash sale (k6, 1000 user đồng thời, quota 100) → bán đúng 100.

### Phase 6 — Realtime & Admin
- [x] P6-1 WebSocket (STOMP over SockJS) + JWT handshake
- [x] P6-2 Thông báo realtime (đơn đổi trạng thái, thanh toán thành công) + lưu bảng `notifications`
- [x] P6-3 Chat buyer ↔ seller (bảng `conversations`, `messages`), danh sách hội thoại, trạng thái đã đọc
- [x] P6-4 Admin: duyệt shop/sản phẩm, quản lý user (khóa/mở), banner trang chủ, voucher sàn, thống kê tổng quan *(sản phẩm: hậu kiểm — đăng là bán ngay, admin khóa/mở kèm lý do; theo quyết định của người dùng)*
- [x] P6-5 FE: chuông thông báo, widget chat, module `admin` (lazy)

**DoD:** seller đổi trạng thái đơn → buyer nhận thông báo realtime; chat 2 chiều hoạt động.

### Phase 7 — Tối ưu & Go-live
- [ ] P7-1 SSR + meta tags/Open Graph cho trang sản phẩm, `sitemap.xml` *(code xong: `Seo` service — title/description/canonical/OG/Twitter/JSON-LD, 404 khi SSR; `/robots.txt`, `/sitemap.xml` ở SSR server + `GET /seo/sitemap`; chưa đo Lighthouse)*
- [ ] P7-2 Cache Redis cho trang chủ, category, product detail (invalidate khi cập nhật) *(code xong: danh mục 1h, chi tiết sản phẩm 10 phút — evict sau commit, danh sách 1 phút; test `PlatformIT`)*
- [ ] P7-3 Rate limit (Bucket4j + Redis) cho login, OTP, checkout *(code xong: login/register/refresh theo IP, checkout/chat/review theo user, 429 + Retry-After; chưa có OTP; test `RateLimitIT`)*
- [ ] P7-4 Rà soát bảo mật theo OWASP Top 10 (XSS trong mô tả/đánh giá, IDOR, CSRF cho cookie refresh) *(rà soát xong, xem `docs/security-review.md`: không còn High, còn 2 mục Medium)*
- [ ] P7-5 Load test k6 các luồng chính, tối ưu query chậm (`EXPLAIN ANALYZE`) *(script k6 `read-apis.js`, `checkout.js` + `node-bench.mjs`; V11 index danh sách theo EXPLAIN ANALYZE; chưa ghi lại số đo p95 @ 500 RPS)*
- [ ] P7-6 Prometheus + Grafana, log có cấu trúc JSON + correlation id *(code xong: `/actuator/prometheus`, log JSON ECS ở prod, `X-Request-Id` trong MDC, dashboard Grafana + cảnh báo)*
- [ ] P7-7 Dockerfile multi-stage cho BE/FE, cấu hình deploy production (S3/R2 thay MinIO, CDN ảnh) *(code xong: Dockerfile BE/FE, `deploy/docker-compose.prod.yml`, nginx TLS; chưa build image — chưa có Docker)*

**DoD:** Lighthouse SEO ≥ 90 cho trang sản phẩm; p95 API đọc < 200ms ở 500 RPS; không còn lỗi bảo mật mức High.

---

## 9. Definition of Done chung (áp dụng mọi task)

- [ ] Build BE và FE không lỗi, không warning mới
- [ ] Có test cho logic mới; `./mvnw verify` và `npm test` pass
- [ ] Endpoint mới có trong Swagger với mô tả request/response
- [ ] Validate input, trả lỗi đúng format ở mục 5
- [ ] Kiểm tra phân quyền và quyền sở hữu tài nguyên
- [ ] UI responsive trên mobile (375px) và desktop

---

## 10. Nhật ký tiến độ

| Ngày | Phase/Task | Nội dung | Ghi chú / Vấn đề tồn |
|---|---|---|---|
| 2026-10-26 | Phase 0 (P0-1 → P0-7) | Khung repo, docker-compose (Postgres 17/Redis 7/MinIO + healthcheck), backend Spring Boot 3.5.16 + Modulith 1.4.14 (module `common` OPEN: ApiResponse/ApiErrorResponse/PageResponse, GlobalExceptionHandler, ErrorCode, Security stateless + 401/403 JSON, CORS 4200, OpenAPI), `V1__init.sql`, frontend Angular 21 SSR + Tailwind 4 + PrimeNG 21 (theme cam) + header/footer responsive, CI GitHub Actions. `./mvnw verify`: 14 unit test pass, 4 IT skip (không có Docker). `npm run build` sạch, `npm test` 4/4 pass. | **Chưa đạt DoD Phase 0:** máy dev chưa có Docker nên chưa chạy `docker compose up`, chưa kiểm tra `/actuator/health` = UP và IT Testcontainers; CI chưa chạy vì chưa có remote GitHub. Quyết định: ghim Angular 21 + PrimeNG 21 (PrimeNG 22 cần license); budget initial FE nâng lên 750 kB vì preset Aura ~114 kB. `products.slug` UNIQUE toàn sàn (cho `GET /products/{slug}`); mọi bảng V1 có `created_at/updated_at`. |
| 2026-10-26 | Phase 0 (bổ sung) | Cài PostgreSQL 17.11 portable trên máy dev: Flyway V1 chạy thành công, `/actuator/health` = UP (tắt health Redis). | Vẫn chờ Docker cho `docker compose up`, Redis, MinIO; CI chờ remote GitHub. |
| 2026-10-26 | Phase 1 (P1-1 → P1-9) | **BE:** auth JWT HS256 (Spring Security resource server) + refresh token xoay vòng lưu SHA-256, cookie httpOnly SameSite=Strict, phát hiện dùng lại token → thu hồi mọi phiên; `/me` + sổ địa chỉ (đúng 1 mặc định, partial unique index, chống IDOR); cây danh mục cache Redis (fail-open, evict sau commit) + admin CRUD tối đa 3 cấp; `/products` lọc danh mục (gồm con)/giá, sort whitelist, phân trang ổn định; chi tiết sản phẩm kèm SKU/ảnh/shop/breadcrumb; upload ảnh MinIO kiểm tra magic bytes; seed dev (ApplicationRunner). Migration **V2** `products.variant_options` (giữ thứ tự nhóm phân loại vì JSONB không giữ thứ tự key), **V3** `addresses.district_code` nullable. **FE:** login/register, AuthStore (NgRx SignalStore, token trong bộ nhớ, khôi phục phiên qua cookie), interceptor tự refresh (gộp request 401 đồng thời), guard theo role; trang chủ, danh mục + lọc/sort/phân trang theo URL, chi tiết sản phẩm chọn phân loại → giá/tồn kho; tài khoản + sổ địa chỉ. SSR cho trang public, CSR cho `/auth`, `/account`. **Test:** BE 58 unit + 24 IT pass (IT chạy trên Postgres local với `-Dit.external=true`), FE 30 unit pass; build không warning; UI kiểm tra ở 375px và desktop. | Địa chỉ dùng **địa giới 2 cấp** (tỉnh → xã, 34 tỉnh/3.321 xã, nguồn provinces.open-api.vn v2) theo quyết định của người dùng — GHN (P4-5) có thể cần ánh xạ mã. MinIO bản 8.5.17 (8.6 dùng OkHttp 5/Kotlin 2 xung đột Spring Boot 3.5). Chưa kiểm tra thật: Redis cache, upload MinIO (chờ Docker/CI). Nút "Thêm vào giỏ" hiện thông báo — giỏ hàng ở Phase 2. |
| 2026-10-26 | i18n (ngoài plan, theo yêu cầu) | FE đa ngôn ngữ vi/en bằng Transloco 8.4: 192 key trong `src/i18n/vi.json` + `en.json`, nạp bằng dynamic import (chạy cả SSR), app initializer nạp ngôn ngữ trước khi render; nút VI/EN ở header (mobile: footer) đổi runtime, lưu cookie `lang`; tiêu đề route là key dịch (TitleStrategy); thông báo lỗi API map theo mã lỗi → `errors.<CODE>`; định dạng số/tiền theo locale (₫189.000 / ₫189,000). Test: key và tham số vi/en khớp nhau, đổi ngôn ngữ ở header; FE 34 unit pass, build không warning; SSR kiểm tra với cookie vi/en. | Dữ liệu (tên sản phẩm, danh mục, địa giới) giữ nguyên tiếng Việt. Thông báo lỗi backend vẫn tiếng Anh nhưng FE không hiển thị trực tiếp (dùng mã lỗi). |
| 2026-10-26 | Mock API FE (ngoài plan, theo yêu cầu) | `npm run start:mock`: interceptor giả lập backend (`src/app/mock`) cho mọi endpoint FE đang dùng (auth, /me, sổ địa chỉ, media, categories, products) với format/mã lỗi/validate như API thật; dữ liệu sinh cố định giống seed dev (20 danh mục, 3 shop, 100 sản phẩm); user/địa chỉ/phiên lưu localStorage; access token giả hết hạn 15 phút để chạy đúng luồng tự refresh. Chỉ có trong cấu hình `mock` (đã kiểm tra bundle production không chứa). FE 41 unit test pass. | Slug/tên sản phẩm mock khác dữ liệu seed của backend (bộ sinh ngẫu nhiên khác) nhưng cùng cấu trúc. Khi thêm endpoint mới ở BE cần thêm handler tương ứng trong mock nếu muốn chạy FE độc lập. |
| 2026-10-26 | Phase 2 (P2-1 → P2-8) | **BE:** migration **V4** (cart_items, orders, order_items, order_status_history — total = subtotal + ship − discount ràng buộc bằng CHECK). Module `cart` (giỏ nhóm theo shop, cộng dồn không vượt tồn kho, chọn theo dòng/shop/tất cả, `/cart/count` cho badge) và `order` (preview + đặt hàng COD tách đơn theo shop trong 1 transaction: trừ kho nguyên tử `UPDATE ... WHERE stock >= :q` theo thứ tự variantId để tránh deadlock, snapshot giá/tên/ảnh/địa chỉ, xóa món đã mua khỏi giỏ, `expectedTotal` → 409 PRICE_CHANGED; đơn mua lọc theo trạng thái, chi tiết + lịch sử; hủy khi PENDING/CONFIRMED có khóa dòng và hoàn kho). State machine `OrderStatus` + `order_status_history`. Mã đơn UUIDv7. Phí ship cố định `app.order.shipping-fee` = 30.000đ/đơn. **FE:** badge giỏ hàng (CartStore), trang giỏ (chọn theo shop/từng món, sửa số lượng, xóa), checkout (đổi/thêm địa chỉ, lời nhắn từng shop, COD), trang đặt hàng thành công, "Đơn mua" (tab trạng thái) + chi tiết đơn (timeline) + hủy; i18n vi/en (280 key); mock API bổ sung giỏ/checkout/đơn. **Test:** BE 92 unit + 29 IT pass trên Postgres (gồm P2-6: 2 checkout đồng thời món cuối → đúng 1 đơn, kho = 0); FE 45 unit pass; **Playwright e2e** luồng mua hàng (đăng ký → thêm giỏ → checkout COD → đơn mua → hủy) + chặn vượt tồn kho, desktop + mobile: pass trên mock và trên backend thật. | Phí ship cố định tới P4-5 (GHN). Đơn của shop đã bị khóa vẫn hiển thị trong lịch sử (shop lấy theo mọi trạng thái). CI có thêm job e2e (Playwright Chromium, mock API) — chưa chạy vì chưa có remote. TanStack Query chưa áp dụng (chờ người dùng quyết). |
| 2026-10-26 | Phase 3 (P3-1 → P3-5) | **BE:** migration **V5** (shops thêm trạng thái `REJECTED` + `reject_reason`, index `shops(status, created_at)`, `orders(shop_id, created_at DESC)`). Module `shop`: mở shop (tạo shop PENDING + nâng BUYER → SELLER trong 1 transaction; client gọi `/auth/refresh` để nhận role mới; slug shop duy nhất, đổi theo tên khi chưa duyệt), `GET/PUT /seller/shop` (shop bị từ chối sửa xong → PENDING), admin `GET /admin/shops?status=`, `approve`, `reject` (bắt buộc lý do). `ShopApi.requireShopIdOfOwner/requireActiveShopIdOfOwner` (ghi chỉ khi shop ACTIVE → 422 `SHOP_NOT_ACTIVE`). Module `catalog`: `/seller/products` CRUD + ẩn/hiện + xóa mềm; `VariantMatrix` kiểm tra tối đa 2 nhóm phân loại, ≤ 200 tổ hợp, mỗi tổ hợp đúng 1 SKU; SKU khớp theo id (có id = sửa, không id = thêm, thiếu = xóa — xóa trước rồi flush để dùng lại mã SKU); SKU tự sinh `S{shopId}-XXXXXXXX`, trùng → 409 `SKU_ALREADY_EXISTS`; chỉ danh mục lá (`CATEGORY_NOT_LEAF`); URL ảnh chỉ http(s); slug `ten-san-pham-{id}`, giữ nguyên khi đổi tên; sửa tồn kho đụng checkout cùng lúc → `@Version` → 409 `CONFLICT` (handler mới cho optimistic lock). Module `order`: `/seller/orders` (lọc trạng thái, `counts`, chi tiết, `actions` theo state machine), `confirm/ship/deliver/cancel` (khóa dòng; seller hủy bắt buộc lý do + hoàn kho; COD chuyển `PAID` khi DELIVERED), `/seller/dashboard?granularity=DAY\|MONTH&from&to` (giờ VN; doanh số = tiền hàng của đơn không hủy/trả, theo ngày đặt; đủ mọi kỳ; top 5 sản phẩm; số đơn theo trạng thái). **FE:** `/seller-center` (lazy, CSR): đăng ký mở shop, layout sidebar (tab cuộn ngang trên mobile) + banner chờ duyệt/bị từ chối/bị khóa, dashboard (việc cần làm, 4 chỉ số, biểu đồ cột doanh số tự vẽ — tooltip hover/focus + xem dạng bảng, top sản phẩm), danh sách sản phẩm (tab trạng thái, tìm tên, ẩn/hiện/xóa, phân trang), form sản phẩm (ảnh upload hoặc dán URL, ảnh bìa, thuộc tính, bật phân loại → bảng SKU sinh theo tổ hợp, giữ giá/kho khi sửa nhóm, áp giá/kho hàng loạt), đơn hàng (tab + số đếm, thao tác ngay trên danh sách, hộp thoại lý do hủy), chi tiết đơn, hồ sơ shop; `/admin/shops` duyệt/từ chối; header có lối vào Kênh người bán / Duyệt shop. i18n vi/en (+~200 key); mock API bổ sung toàn bộ endpoint seller/admin (role nằm trong token giả như JWT). **Test:** BE 126 unit + 32 IT pass trên Postgres local (`SellerCenterIT`: mở shop → duyệt → đăng sản phẩm 4 SKU → hiển thị phía buyer → mua → xử lý tới DELIVERED → dashboard; từ chối/gửi lại; validate; IDOR giữa 2 seller); FE 56 unit pass, build không warning; **Playwright** `seller.e2e.ts` (luồng DoD + từ chối/gửi lại) + `purchase.e2e.ts`: 8/8 pass trên mock, desktop + mobile; UI kiểm tra bằng ảnh chụp ở 375px và desktop. | Ngoài plan nhưng cần cho luồng duyệt: trạng thái shop `REJECTED` + gửi duyệt lại, seller hủy đơn kèm lý do, COD → PAID khi giao thành công. Khóa/mở shop, duyệt sản phẩm để Phase 6 (sản phẩm đăng là ACTIVE ngay). Danh sách sản phẩm public chưa lọc theo trạng thái shop (hiện shop chỉ rời ACTIVE khi bị khóa — P6 cần xử lý). Chưa chạy e2e trên backend thật: backend dev đang chạy là bản jar cũ, cần build lại + khởi động lại (Flyway tự chạy V5). Ảnh upload vẫn cần MinIO; form cho phép dán URL ảnh để thử ở dev. |
| 2026-10-27 | Phase 4 (P4-1 → P4-6, một phần) | Theo quyết định của người dùng: GHN + phí nội bộ dự phòng; chưa có tài khoản sandbox. **BE:** migration **V6** (`payments` — `idempotency_key` = mã giao dịch gửi cổng; index đơn online chưa thanh toán; `shops.pickup_*`; `shipments` + `shipment_events`). Module **payment** (mới): `PaymentProvider` (strategy) — VNPay 2.1.0 (HMAC-SHA512, chuỗi sắp xếp + URL-encode US-ASCII, giờ GMT+7, `vnp_ExpireDate`) và MoMo v2 `captureWallet` (HMAC-SHA256, `orderExpireTime`); return URL + IPN dùng chung logic xác nhận idempotent (khóa dòng theo mã giao dịch, kiểm tra số tiền; IPN VNPay trả `00/01/02/04/97`, MoMo `204`); phát `PaymentSucceeded` trong cùng transaction; **cổng giả lập** (`app.payment.sandbox-gateway`, bật ở dev/test) ký callback bằng key cấu hình và đi qua đúng luồng kiểm tra chữ ký. Module **order**: checkout VNPAY/MOMO trả `paymentUrl` + hạn 15 phút; seller không xác nhận được đơn chưa thanh toán (`ORDER_AWAITING_PAYMENT`); `GET/POST /checkout/{id}/payment` (trạng thái / thanh toán lại trong hạn); job `@Scheduled` hủy đơn quá hạn + hoàn kho (SKIP LOCKED, chạy nhiều instance an toàn); hủy đơn đã thanh toán online → `REFUNDED`. Module **shipping** (mới): `ShippingProvider` — GHN (`/v2/shipping-order/fee|create`, `switch-status/cancel`, tên tỉnh/phường + `is_new_to_address`) và nội bộ (cùng tỉnh 22k / cùng miền 30k / khác miền 38k + 5k mỗi 500g, mã `SVX…`); phí ship checkout tính từ địa chỉ lấy hàng của shop; seller xác nhận → tạo vận đơn (COD thì thu hộ), giao/đã giao → hành trình; webhook GHN (`?token=`, chống trùng, đẩy đơn sang SHIPPING/DELIVERED). Dữ liệu địa giới đóng gói ở backend để đổi mã → tên. `PUT /seller/shop/pickup-address`. **FE:** chọn phương thức thanh toán ở checkout, trang cổng giả lập `/payment/sandbox`, trang kết quả `/payment/result` (hỏi lại trạng thái khi IPN chậm, đếm ngược hạn, thanh toán lại), "Chờ thanh toán" + "Thanh toán ngay" ở Đơn mua, hành trình vận chuyển ở chi tiết đơn (buyer + seller), địa chỉ lấy hàng ở hồ sơ shop; mock API bổ sung đầy đủ; i18n vi/en. **Test:** BE 149 unit + 36 IT pass trên Postgres local (`PaymentShippingIT`: VNPay qua cổng giả lập, IPN lặp → 02, sai chữ ký 97, sai tiền 04, không có giao dịch 01; MoMo thất bại → thanh toán lại → IPN 204 x2; hủy đơn quá hạn + hoàn kho; phí theo vùng; vận đơn + webhook); FE 57 unit pass, build không warning; Playwright 10/10 pass trên mock (desktop + mobile, thêm `payment.e2e.ts`). | **Chưa đạt DoD Phase 4:** chưa thử với VNPay/MoMo/GHN sandbox thật (cần tài khoản → điền `.env`, đặt `PAYMENT_SANDBOX_GATEWAY=false`, `PAYMENT_PUBLIC_BASE_URL` phải truy cập được từ Internet để nhận IPN). Tên trường GHN cho địa giới 2 cấp là phỏng đoán theo hướng dẫn GHN + tài liệu cộng đồng. Hoàn tiền chỉ đánh dấu `REFUNDED`, chưa gọi API hoàn tiền của cổng. Khối lượng kiện ước tính 500g/sản phẩm (sản phẩm chưa có trường khối lượng). Một tài khoản GHN chung cho cả sàn. Backend dev đang chạy là bản cũ — cần build/khởi động lại (Flyway chạy V5, V6). |
| 2026-10-27 | Phase 5 (P5-1 → P5-7) | Theo quyết định của người dùng: hạ tầng portable trên máy dev (không Docker) — Elasticsearch 8.18.8, Kafka 3.9.2 KRaft, Garnet 2.2.4 (thay Redis, có Lua) trong `~/.shopvi-infra`; docker-compose thêm `elasticsearch` + `kafka`. **BE:** migration **V7** (`event_publication` của Spring Modulith, `reviews`), **V8** (`vouchers`, `voucher_usages`, `orders.vouchers` JSONB), **V9** (`flash_sales`, `flash_sale_items`, `flash_sale_reservations`). Event publication registry (JPA) + `@Externalized` → Kafka (payload JSON byte, `ByteArrayJsonMessageConverter` dùng chung cho producer/listener). Module **search** (mới): chỉ mục `products` với analyzer `vi_folded` (lowercase + asciifolding, đ→d), `name.suggest` search_as_you_type; `ProductChanged` (tạo/sửa/ẩn/xóa/kho/điểm) → Kafka → indexer; reindex toàn bộ bằng alias swap (`POST /admin/search/reindex`, tự tạo khi khởi động nếu chưa có alias); `GET /search` (multi_match AND + fuzziness, lọc danh mục/giá, sort, highlight `<mark>` đã escape), `GET /search/suggest`; ES lỗi → 503 `SEARCH_UNAVAILABLE`. Module **promotion** (mới): voucher sàn/shop (FIXED/PERCENT/FREESHIP, đơn tối thiểu, giảm tối đa, tổng lượt + lượt mỗi người), áp ở preview/đặt hàng — voucher shop trước, voucher sàn chia theo tỷ lệ cho từng đơn; trừ lượt bằng `UPDATE … WHERE used_count < usage_limit` (chống race), hoàn lượt khi hủy đơn; lỗi kèm lý do (`MIN_ORDER`, `USER_LIMIT`…). Flash sale: admin tạo/hủy phiên; quota trên Redis (`fs:{itemId}:stock/users/meta`), giữ suất bằng **Lua** (hết suất / quá giới hạn / ngoài giờ trong 1 bước nguyên tử), rate limit 5 req/s/người; `POST /flash-sales/items/{id}/purchase` → 202 + reservationId, gửi `FlashSaleOrderRequested` (Kafka, đồng bộ) → order module tạo đơn COD giá sale (reservationId = checkoutId, xử lý lặp không tạo đơn trùng); mọi lượt giữ kết thúc bằng đúng 1 dòng CONFIRMED/FAILED (khóa chính chặn chạy đua), FAILED thì trả suất về Redis; nạp lại Redis từ DB khi mất key. Module **review** (mới): đánh giá món của đơn COMPLETED 1 lần (sao, nội dung, ≤ 5 ảnh), che tên người viết, cập nhật điểm sản phẩm + shop, seller phản hồi 1 lần; `POST /orders/{code}/complete` (người mua xác nhận đã nhận hàng). **FE:** gợi ý khi gõ ở header (bàn phím ↑/↓/Enter, ARIA combobox), trang kết quả tìm kiếm (sắp xếp, lọc giá, phân trang theo URL), voucher ở checkout (nhập mã / chọn từ danh sách, giảm theo shop, lý do không áp được), khu Flash Sale trang chủ (đếm ngược, thanh đã bán, mua → hỏi trạng thái → mở đơn), đánh giá ở trang sản phẩm (tổng quan theo sao, lọc, phản hồi của shop), "Đã nhận hàng" + form đánh giá ở chi tiết đơn, Seller: Voucher + Đánh giá (phản hồi), Admin: tab Duyệt shop / Voucher sàn / Flash sale; mock API + i18n vi/en đầy đủ. **Test:** BE 155 unit + 45 IT pass trên Postgres/Garnet/Kafka/ES local (`FlashSaleIT`: **1000 request đồng thời, quota 100 → đúng 100 đơn**, kho trừ đúng 100, giá sale; giới hạn mỗi người; đơn lỗi trả suất; hủy phiên. `VoucherIT`: 10 đơn đồng thời, voucher 3 lượt → đúng 3. `SearchIT`: gõ không dấu ra tên có dấu, ẩn/hiện sản phẩm → chỉ mục cập nhật qua Kafka. `ReviewIT`); FE 61 unit pass, build không warning; Playwright 14/14 pass trên mock (thêm `promotion.e2e.ts`: tìm "ao thun" → "Áo Thun", voucher, flash sale). Script k6 `loadtest/flash-sale.js`. | DoD "1000 user, quota 100 → đúng 100" kiểm bằng IT (HTTP thật, 100 luồng); **chưa chạy k6** (máy chưa cài k6). IT và backend dev không nên chạy cùng lúc trên cùng Kafka (cùng tên topic). Form đánh giá FE chưa tải ảnh. Admin nhập id phân loại (SKU) khi tạo flash sale (chưa có ô tìm sản phẩm). Phase 0/4 vẫn còn tồn như ghi ở trên. |
| 2026-10-27 | Phase 6 (P6-1 → P6-5) | Người dùng chọn **hậu kiểm** sản phẩm; đồng ý tải `spring-boot-starter-websocket`, `@stomp/stompjs`, `sockjs-client`. **BE:** migration **V10** (`notifications` — loại + tham số JSONB để FE dịch theo ngôn ngữ; `conversations` (UNIQUE buyer–shop, số chưa đọc + tin cuối đã xem mỗi bên), `messages`, `banners`, `users.lock_reason`, `shops.ban_reason`, `products.ban_reason`). Module **notification** (mới): STOMP over SockJS `/ws` (broker trong bộ nhớ), xác thực bằng JWT trong khung CONNECT (`StompAuthInterceptor`: kiểm chữ ký + tài khoản còn hoạt động), chỉ cho SUBSCRIBE `/user/queue/**`, không nhận SEND (gửi dữ liệu qua REST); tin đẩy sau khi transaction commit. Thông báo: `@ApplicationModuleListener` trên sự kiện mới của order (`OrderStatusChanged` — mọi chỗ đổi trạng thái đi qua `OrderEvents`, `OrderPlaced`, `OrderPaid`), shop (`ShopStatusChanged`), catalog (`ProductModerated`) → lưu + đẩy `/user/queue/notifications`; REST danh sách / số chưa đọc / đọc / đọc tất cả. Chat: mở hội thoại (chạy đua 2 request → UNIQUE + transaction riêng), danh sách gộp phía người mua và phía shop của tôi, tin nhắn phân trang theo id, gửi (khóa dòng hội thoại), đánh dấu đã đọc → đẩy `MESSAGE` / `READ` tới `/user/queue/chat` của cả hai bên; không chat với shop của chính mình. Admin: khóa/mở tài khoản (thu hồi refresh token ngay, không khóa ADMIN), khóa/mở shop (sản phẩm biến khỏi danh sách công khai + chỉ mục tìm kiếm qua Kafka), hậu kiểm sản phẩm (`/admin/products` lọc trạng thái/tên, khóa kèm lý do → seller nhận thông báo, không tự mở lại được), banner (`GET /banners` theo thời gian hiển thị, CRUD admin, chặn URL không phải http(s)), thống kê tổng quan (`/admin/stats?days`, module **admin** mới: người dùng, shop, sản phẩm theo trạng thái, đơn + GMV từng ngày giờ VN). **FE:** kênh realtime trừu tượng (`RealtimeService`): bản STOMP thật (nạp động thư viện khi đăng nhập — bundle đầu vẫn dưới budget; làm mới token trước khi kết nối lại; polyfill `global` cho sockjs-client) và bản mock (BroadcastChannel giữa các tab); chuông thông báo trên header (badge, danh sách, đọc / đọc tất cả, toast khi có tin mới, bấm mở đúng trang), widget chat nổi (danh sách hội thoại, khung tin, "Đã xem", mobile toàn màn hình), nút "Chat ngay" ở trang sản phẩm; khu admin có tab: Tổng quan (biểu đồ GMV/đơn), Shop (thêm khóa/mở), Sản phẩm, Tài khoản, Voucher sàn, Flash sale, Banner; banner trang chủ lấy từ API (không có thì dùng slide mẫu); seller thấy lý do sản phẩm bị khóa. Mock API + i18n vi/en đầy đủ. **Test:** BE 158 unit + 53 IT pass (`RealtimeIT` dùng client STOMP over SockJS thật: token sai bị từ chối; **seller xác nhận đơn → người mua nhận thông báo realtime**; người mua hủy → seller nhận; **chat 2 chiều** + "đã xem" + phân quyền. `AdminModerationIT`: khóa tài khoản / sản phẩm / shop, banner, thống kê); FE 65 unit pass, build không warning; Playwright 16/16 (thêm `realtime.e2e.ts`: 2 tab người mua + seller). Kiểm tra tay trên backend thật + `ng serve`: seller xác nhận → chuông người mua lên 1 và toast hiện ngay (phát hiện và sửa lỗi `global is not defined` của sockjs-client mà test mock không bắt được). | Broker STOMP trong bộ nhớ: chạy nhiều instance cần broker relay (RabbitMQ/ActiveMQ). Access token đã cấp của tài khoản bị khóa còn hiệu lực tối đa 15 phút (REST), WebSocket đang mở không bị ngắt ngay. Trang Đơn mua không tự cập nhật trạng thái khi có thông báo (bấm thông báo để mở đơn). Chat chưa gửi ảnh / sản phẩm, chưa có chỉ báo "đang gõ". Menu tài khoản admin: mục "Duyệt shop" đổi thành "Quản trị" (mở trang Tổng quan). |
| 2026-10-27 | Phase 7 (P7-1 → P7-7, rà soát) | Đối chiếu code với plan: toàn bộ P7 đã có trong commit `4e1cffc` (SEO/SSR + sitemap/robots, cache Redis có tiền tố khóa theo môi trường, rate limit Bucket4j, `RefreshOriginFilter` chống CSRF, header bảo mật, V11 index danh sách, script tải, Prometheus/Grafana/cảnh báo, log JSON + correlation id, Dockerfile + compose prod + nginx) nhưng chưa ghi vào plan. Bổ sung `docs/security-review.md` (đang được `server.ts` tham chiếu nhưng thiếu) và mục Phase 7 trong README. | Máy hiện tại (cài JDK 21.0.12 + Node 22.23 portable ở `~/.shopvi-tools`): `./mvnw verify` BUILD SUCCESS, 158 unit test pass, 63 IT bị bỏ qua (không có Docker/hạ tầng — gồm `PlatformIT`, `RateLimitIT` của P7). FE: `package-lock.json` trỏ `resolved` tới registry nội bộ `http://10.60.129.132/repository/npm-proxy/` (máy này và GitHub Actions không truy cập được) → đổi sang `https://registry.npmjs.org/` (integrity giữ nguyên, theo đồng ý của người dùng); `npm test` 65/65 pass, build không warning (initial 737 kB). **Sửa lỗi P7-1:** Angular 21 SSR chặn host không khai báo (`allowedHosts: []`) và header `X-Forwarded-For` của nginx → mọi trang rơi về CSR, HTML trả về không có thẻ SEO; `server.ts` nay cho phép host của `SITE_URL` + localhost (hoặc `NG_ALLOWED_HOSTS`) và tin `X-Forwarded-Proto/For` — kiểm tra: canonical/OG có trong HTML SSR, sau proxy canonical là `https://<SITE_URL>/`, host lạ bị từ chối. **Chưa tick P7:** IT chưa chạy (cần Docker/hạ tầng). **Chưa đạt DoD Phase 7:** chưa đo Lighthouse SEO, chưa ghi số đo p95 @ 500 RPS, chưa build image Docker. Bảo mật còn 2 mục Medium: CSP `script-src` cho SSR, access token của tài khoản bị khóa còn hiệu lực ≤ 15 phút. |
| 2026-10-27 | Rà soát Phase 0 → 7 | Đối chiếu từng mục với code: mọi endpoint trong plan đều có, đủ `@Operation` Swagger; đủ index bắt buộc ở mục 6; compose có healthcheck cho mọi service. **Bổ sung:** (1) trang **Quản trị → Danh mục** (cây 3 cấp: thêm gốc/con, sửa tên/slug/thứ tự/icon, xóa danh mục rỗng; mục 1 giao việc quản lý danh mục cho ADMIN, API có từ P1-3 nhưng FE chưa có) + mock API cùng quy tắc; (2) form đánh giá **tải ảnh** (≤ 5 ảnh, jpg/png/webp ≤ 5MB — P5-6 yêu cầu upload ảnh); (3) CI nâng action lên v5 (v4 chạy Node 20 đã deprecated). **Kiểm tra:** FE 69 unit pass, build không warning, Playwright 16/16 (desktop + mobile, Edge); UI danh mục kiểm ở desktop và 375px. | CI trên GitHub (lần chạy duy nhất, commit `4e1cffc`) **đỏ**: FE do registry nội bộ trong lockfile (đã sửa), BE `mvn verify` lỗi khi chạy IT bằng Testcontainers — cần log (GitHub yêu cầu đăng nhập) hoặc chạy IT local để tìm test lỗi. Testcontainers cho ES/Kafka chưa từng chạy thành công (trước đây IT chỉ chạy bằng hạ tầng portable). Mock: thay đổi danh mục chỉ giữ trong bộ nhớ. |

---

## 11. Backlog (ý tưởng ngoài phạm vi hiện tại)

- Đăng nhập Google/Facebook (OAuth2), đăng nhập bằng SĐT + OTP
- Ví nội bộ / ShopeePay-like, ZaloPay
- Gợi ý sản phẩm bằng `pgvector` (embedding) — "Có thể bạn cũng thích"
- Livestream bán hàng, Shopee Feed
- App mobile (dùng chung API)
- Tách microservice: `order`, `payment`, `search` là ứng viên đầu tiên
- Job dọn `refresh_tokens` đã hết hạn/thu hồi (bảng sẽ phình theo số lần đăng nhập/refresh)
- Ánh xạ mã địa giới 2 cấp (2025) sang mã của hãng vận chuyển nếu GHN còn dùng mã quận/huyện
- Trang shop công khai `/shop/{slug}` (danh sách sản phẩm của shop, thông tin shop)
- Ảnh riêng cho từng SKU (cột `product_variants.image_url` đã có, form seller chưa hỗ trợ)
- Lọc "Hết hàng" / "Sắp hết hàng" trong danh sách sản phẩm của seller
- Gọi API hoàn tiền của VNPay/MoMo khi hủy đơn đã thanh toán (hiện chỉ đánh dấu `REFUNDED`) + đối soát giao dịch
- Trường khối lượng/kích thước cho sản phẩm (phí ship đang ước tính 500g/sản phẩm)
- Mỗi seller một cửa hàng GHN riêng (hiện dùng chung một `ShopId` của sàn)
- Ô tìm sản phẩm khi admin tạo flash sale
- Tiền tố tên topic Kafka theo môi trường (dev/IT dùng chung broker local)
- STOMP broker relay (RabbitMQ) khi chạy nhiều instance; ngắt WebSocket của tài khoản vừa bị khóa
- Sitemap index khi vượt 45.000 sản phẩm; OWASP Dependency-Check / `npm audit` trong CI
- Chat: gửi ảnh / thẻ sản phẩm / đơn hàng, chỉ báo "đang gõ"; trang Đơn mua tự cập nhật khi có thông báo đổi trạng thái
