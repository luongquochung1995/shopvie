# Chạy ShopVi trên máy Windows làm máy chủ LAN (không Docker)

Một máy Windows chạy toàn bộ hệ thống; máy khác trong cùng mạng mở `http://<IP máy chủ>`.

```
Máy khác ──HTTP :80──> nginx ─┬─ /api, /ws ─> backend (Spring Boot, profile prod)  127.0.0.1:8080
                              ├─ /uploads  ─> MinIO                                  127.0.0.1:9000
                              └─ /         ─> frontend SSR (Node)                     127.0.0.1:4000
backend ─> PostgreSQL 17 :5433 · Garnet (Redis) :6379 · Elasticsearch :9200 · Kafka :9092  (đều 127.0.0.1)
```

## Cài một lần

Hạ tầng bản portable trong `%USERPROFILE%\.shopvi-infra`, công cụ build trong `%USERPROFILE%\.shopvi-tools`:

| Thư mục | Nguồn |
|---|---|
| `.shopvi-tools\jdk-21*`, `node-v22*` | Temurin JDK 21, Node.js 22 (zip) |
| `.shopvi-infra\pgsql`, `pgdata` | PostgreSQL 17 "binaries" zip (EnterpriseDB) — tạo DB: xem README gốc |
| `.shopvi-infra\garnet\net10.0`, `dotnet` | Microsoft Garnet `win-x64-based-readytorun.zip` + .NET 10 runtime zip |
| `.shopvi-infra\minio\minio.exe` | `pgsty/minio` release `windows_amd64` (bản `minio/minio` đã ngừng phát hành) |
| `.shopvi-infra\elasticsearch-8.18.8` | Elasticsearch Windows zip; thêm vào `config\elasticsearch.yml`: `discovery.type: single-node`, `network.host: 127.0.0.1`, `xpack.security.enabled: false`, `xpack.ml.enabled: false` |
| `.shopvi-infra\kafka_2.13-3.9.2` | Kafka tgz; tạo `config\kraft\shopvi.properties` (KRaft, `listeners=PLAINTEXT://127.0.0.1:9092,CONTROLLER://127.0.0.1:9093`, `log.dirs` trong `.shopvi-infra\data\kafka`) rồi format storage một lần |
| `.shopvi-infra\nginx-1.30.6` | nginx for Windows (nginx.org) |

File `.env` ở thư mục gốc repo (tạo từ `.env.example`, mật khẩu ngẫu nhiên, `DB_PORT=5433`).

Build:

```bash
cd backend && ./mvnw -DskipTests package
cd frontend && npm ci && npm run build
```

## Chạy / dừng

```powershell
powershell -ExecutionPolicy Bypass -File deploy\windows\start-server.ps1
powershell -ExecutionPolicy Bypass -File deploy\windows\stop-server.ps1
```

`start-server.ps1` tự lấy IP LAN (hoặc `-PublicHost 192.168.x.y`), bật lần lượt từng thành phần và chờ cổng mở.
Log: `%USERPROFILE%\.shopvi-infra\logs`.

Cấu hình khác production thật: HTTP (không TLS) nên cookie refresh không gắn `Secure`; thanh toán dùng cổng giả lập
(chưa có tài khoản VNPay/MoMo); ảnh upload phục vụ qua `http://<IP>/uploads` (không dùng `/media` — trùng thư mục font của bản build Angular).

## Đưa ra Internet miễn phí (Cloudflare Quick Tunnel, HTTPS)

```powershell
powershell -ExecutionPolicy Bypass -File deploy\windows\start-server.ps1 -Tunnel
```

Cần `cloudflared.exe` trong `.shopvi-infra\cloudflared` (release `cloudflared-windows-amd64.exe` trên GitHub của Cloudflare).
Script mở tunnel tới nginx cổng 80 trước, lấy link `https://<ngẫu-nhiên>.trycloudflare.com` (lưu ở
`.shopvi-infra\tunnel-url.txt`), rồi chạy backend/SSR với link đó là địa chỉ công khai (CORS, canonical, sitemap, ảnh).
Không cần tài khoản Cloudflare, không mở port router; máy này phải luôn bật.

- Link **đổi mỗi lần tạo tunnel mới** (`stop-server.ps1` dừng cả tunnel). Muốn link cố định: tài khoản Cloudflare
  (miễn phí) + tên miền riêng → named tunnel.
- Tên miền mới có thể mất vài phút để DNS của mạng gia đình nhận ra.
- Quick Tunnel dành cho thử nghiệm / demo (không có SLA, giới hạn số request đồng thời).
- Cookie refresh vẫn không gắn `Secure` để truy cập LAN bằng HTTP còn đăng nhập được.

## Mở cổng 80 cho máy khác

Windows Firewall chặn kết nối vào mặc định (nhất là khi mạng đặt là **Public**). Chạy **một lần** trong PowerShell
quyền Administrator:

```powershell
New-NetFirewallRule -DisplayName "ShopVi HTTP 80" -Direction Inbound -Protocol TCP -LocalPort 80 -Action Allow -Profile Private,Public
```

Hoặc khi Windows hỏi lúc nginx khởi động lần đầu, chọn **Allow access**. Gỡ: `Remove-NetFirewallRule -DisplayName "ShopVi HTTP 80"`.

IP Wi-Fi có thể đổi sau khi khởi động lại router — đặt IP tĩnh / DHCP reservation cho máy chủ, chạy lại `start-server.ps1`.
