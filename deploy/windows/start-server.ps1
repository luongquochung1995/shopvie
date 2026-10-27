<#
  Chạy ShopVi trên một máy Windows làm máy chủ cho mạng LAN (không cần Docker):
  PostgreSQL 17, Garnet (Redis), MinIO, Elasticsearch, Kafka — bản portable trong $Infra —
  rồi backend (profile prod), frontend SSR và nginx cổng 80. Chỉ nginx nhận kết nối từ máy khác;
  các dịch vụ còn lại nghe 127.0.0.1.

  Dùng:  powershell -ExecutionPolicy Bypass -File deploy\windows\start-server.ps1 [-PublicHost 192.168.0.101]
  Dừng:  powershell -ExecutionPolicy Bypass -File deploy\windows\stop-server.ps1

  Cần: đã build backend (backend\target\*.jar) và frontend (frontend\dist), file .env ở thư mục gốc repo,
  cùng hạ tầng đã cài theo deploy\windows\README.md. Log: $Infra\logs.
#>
param(
    [string]$PublicHost,
    # Đưa ra Internet qua Cloudflare Quick Tunnel (HTTPS, miễn phí, link *.trycloudflare.com đổi mỗi lần tạo tunnel mới)
    [switch]$Tunnel,
    [string]$Infra = (Join-Path $HOME '.shopvi-infra'),
    [string]$Tools = (Join-Path $HOME '.shopvi-tools')
)
$ErrorActionPreference = 'Stop'
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Logs = Join-Path $Infra 'logs'
$Pids = Join-Path $Infra 'pids'
New-Item -ItemType Directory -Force $Logs, $Pids | Out-Null

if (-not $PublicHost) {
    $PublicHost = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254*' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1).IPAddress
}
$Site = "http://$PublicHost"
Write-Host "ShopVi server: $Site" -ForegroundColor Cyan

# ----- .env → biến môi trường của tiến trình này (các tiến trình con kế thừa)
Get-Content (Join-Path $Repo '.env') | Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$' } | ForEach-Object {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
}

# ----- Cloudflare Quick Tunnel: lấy link trước khi chạy backend/SSR (cần cho CORS, host SSR, URL tuyệt đối)
$PublicSite = $Site
$TunnelHost = $null
if ($Tunnel) {
    $UrlFile = Join-Path $Infra 'tunnel-url.txt'
    $PidFile = Join-Path $Pids 'tunnel.pid'
    $alive = (Test-Path $PidFile) -and (Get-Process -Id (Get-Content $PidFile) -ErrorAction SilentlyContinue)
    if (-not ($alive -and (Test-Path $UrlFile))) {
        $log = Join-Path $Logs 'tunnel.log'
        Remove-Item $log, $UrlFile -ErrorAction SilentlyContinue
        $p = Start-Process -FilePath (Join-Path $Infra 'cloudflared\cloudflared.exe') -WindowStyle Hidden -PassThru `
            -ArgumentList @('tunnel', '--no-autoupdate', '--url', 'http://127.0.0.1:80', '--logfile', "`"$log`"")
        Set-Content $PidFile $p.Id
        $deadline = (Get-Date).AddSeconds(60)
        while (-not (Test-Path $UrlFile)) {
            if ((Get-Date) -gt $deadline) { throw "Không lấy được link Cloudflare Tunnel — xem $log" }
            Start-Sleep -Seconds 1
            $m = Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($m) { Set-Content $UrlFile $m.Matches[0].Value }
        }
    }
    $PublicSite = (Get-Content $UrlFile).Trim()
    $TunnelHost = ([Uri]$PublicSite).Host
    Write-Host "Cloudflare Tunnel: $PublicSite" -ForegroundColor Cyan
}

$Java = Get-ChildItem (Join-Path $Tools 'jdk-21*\bin\java.exe') | Select-Object -First 1 -ExpandProperty FullName
$Node = Get-ChildItem (Join-Path $Tools 'node-v22*\node.exe') | Select-Object -First 1 -ExpandProperty FullName

function Test-Port([int]$Port) {
    [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Port([string]$Name, [int]$Port, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while (-not (Test-Port $Port)) {
        if ((Get-Date) -gt $deadline) { throw "$Name chưa mở cổng $Port sau $TimeoutSec giây — xem $Logs" }
        Start-Sleep -Milliseconds 500
    }
    Write-Host ("  {0,-14} :{1}" -f $Name, $Port) -ForegroundColor Green
}

# Chạy nền (ẩn cửa sổ), log ra $Logs\<name>.log, lưu PID để stop-server.ps1 dừng
function Start-Component([string]$Name, [int]$Port, [string]$Exe, [string[]]$Arguments, [string]$WorkDir, [int]$TimeoutSec = 120) {
    if (Test-Port $Port) { Write-Host ("  {0,-14} :{1} (đang chạy)" -f $Name, $Port); return }
    $opts = @{
        FilePath = $Exe; WorkingDirectory = $WorkDir; WindowStyle = 'Hidden'; PassThru = $true
        RedirectStandardOutput = (Join-Path $Logs "$Name.log"); RedirectStandardError = (Join-Path $Logs "$Name.err.log")
    }
    # Start-Process không nhận ArgumentList rỗng (vd. elasticsearch.bat)
    if ($Arguments) { $opts.ArgumentList = $Arguments }
    $p = Start-Process @opts
    Set-Content (Join-Path $Pids "$Name.pid") $p.Id
    Wait-Port $Name $Port $TimeoutSec
}

Write-Host 'Hạ tầng:'
# PostgreSQL (pg_ctl tự chạy nền)
if (-not (Test-Port 5433)) {
    # Không pipe output của pg_ctl: tiến trình postgres con giữ handle của pipe → lệnh chờ mãi
    Start-Process -FilePath (Join-Path $Infra 'pgsql\bin\pg_ctl.exe') -WindowStyle Hidden -ArgumentList @(
        '-D', "`"$(Join-Path $Infra 'pgdata')`"", '-o', '"-p 5433 -c listen_addresses=localhost"',
        '-l', "`"$(Join-Path $Logs 'postgres.log')`"", 'start') | Out-Null
}
Wait-Port 'postgres' 5433

# Garnet (Redis tương thích, có Lua) cần .NET 10 runtime — bản portable trong $Infra\dotnet
$env:DOTNET_ROOT = Join-Path $Infra 'dotnet'
Start-Component 'redis' 6379 (Join-Path $Infra 'garnet\net10.0\GarnetServer.exe') @(
    '--bind', '127.0.0.1', '--port', '6379', '--lua', '--lua-transaction-mode',
    '--auth', 'Password', '--password', $env:REDIS_PASSWORD) (Join-Path $Infra 'garnet\net10.0')

New-Item -ItemType Directory -Force (Join-Path $Infra 'data\minio') | Out-Null
Start-Component 'minio' 9000 (Join-Path $Infra 'minio\minio.exe') @(
    'server', (Join-Path $Infra 'data\minio'), '--address', '127.0.0.1:9000', '--console-address', '127.0.0.1:9001') $Infra

# Elasticsearch / Kafka chưa cài → backend vẫn chạy nhưng tắt tìm kiếm / event bus (flash sale cần Kafka)
$Degraded = @()
$env:ES_JAVA_OPTS = '-Xms1g -Xmx1g'
$Es = Get-ChildItem (Join-Path $Infra 'elasticsearch-*') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if ($Es) {
    # Ghi đè ES_HOME / ES_PATH_CONF hệ thống (nếu máy từng cài Elasticsearch khác) cho riêng tiến trình này
    $env:ES_HOME = $Es
    $env:ES_PATH_CONF = Join-Path $Es 'config'
    Start-Component 'elasticsearch' 9200 (Join-Path $Es 'bin\elasticsearch.bat') @() $Es 180
} else {
    Write-Host '  elasticsearch  (chưa cài — tắt tìm kiếm)' -ForegroundColor Yellow
    $Degraded += '--app.search.enabled=false', '--management.health.elasticsearch.enabled=false'
}

$Kafka = Get-ChildItem (Join-Path $Infra 'kafka_*') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if ($Kafka -and (Test-Path (Join-Path $Kafka 'config\kraft\shopvi.properties'))) {
    Start-Component 'kafka' 9092 $Java @('-Xms512m', '-Xmx1g', '-cp', "`"$Kafka\libs\*`"", 'kafka.Kafka', "`"$Kafka\config\kraft\shopvi.properties`"") $Kafka
} else {
    Write-Host '  kafka          (chưa cài — tắt event bus, flash sale không mua được)' -ForegroundColor Yellow
    $Degraded += '--spring.modulith.events.externalization.enabled=false', '--spring.kafka.listener.auto-startup=false'
}

Write-Host 'Ứng dụng:'
# Backend: profile prod, chạy sau nginx bằng HTTP trong LAN (cookie refresh không gắn Secure),
# thanh toán qua cổng giả lập (chưa có tài khoản VNPay/MoMo), ảnh MinIO phục vụ qua nginx /uploads
$env:CORS_ALLOWED_ORIGINS = (@($Site, $PublicSite, 'http://localhost') | Select-Object -Unique) -join ','
$env:FRONTEND_BASE_URL = $PublicSite
$env:PAYMENT_PUBLIC_BASE_URL = $PublicSite
$env:REFRESH_COOKIE_SECURE = 'false'
$env:MEDIA_PUBLIC_BASE_URL = "$PublicSite/uploads"
$env:MEDIA_MANAGE_BUCKET = 'true'
$env:MEDIA_URL_INCLUDES_BUCKET = 'true'
$Jar = Get-ChildItem (Join-Path $Repo 'backend\target\shopvi-backend-*.jar') | Select-Object -First 1 -ExpandProperty FullName
Start-Component 'backend' 8080 $Java (@(
    '-Xms1g', '-Xmx2g', '-jar', "`"$Jar`"", '--spring.profiles.active=prod', '--server.address=127.0.0.1',
    '--management.server.address=127.0.0.1', '--app.payment.sandbox-gateway=true',
    '--app.payment.vnpay.tmn-code=SHOPVILAN', "--app.payment.vnpay.hash-secret=$env:JWT_SECRET",
    '--app.payment.momo.partner-code=SHOPVILAN', '--app.payment.momo.access-key=shopvi-lan',
    "--app.payment.momo.secret-key=$env:JWT_SECRET") + $Degraded) $Repo 180

$env:PORT = '4000'
$env:HOST = '127.0.0.1'
$env:API_ORIGIN = 'http://127.0.0.1:8080'
$env:SITE_URL = $PublicSite
$env:NG_ALLOWED_HOSTS = (@($PublicHost, $TunnelHost, $env:COMPUTERNAME.ToLower(), 'localhost', '127.0.0.1') | Where-Object { $_ }) -join ','
$env:NODE_ENV = 'production'
Start-Component 'frontend' 4000 $Node @("`"$Repo\frontend\dist\shopvi-frontend\server\server.mjs`"") (Join-Path $Repo 'frontend')

$Nginx = Get-ChildItem (Join-Path $Infra 'nginx-*') -Directory | Select-Object -First 1 -ExpandProperty FullName
# include mime.types tính theo thư mục của file cấu hình → chép vào conf\ của nginx
Copy-Item (Join-Path $PSScriptRoot 'nginx-lan.conf') (Join-Path $Nginx 'conf\shopvi-lan.conf') -Force
Start-Component 'nginx' 80 (Join-Path $Nginx 'nginx.exe') @('-p', "`"$Nginx`"", '-c', 'conf/shopvi-lan.conf') $Nginx

Write-Host ''
Write-Host "Xong. Mở $Site từ máy khác trong cùng mạng (cần mở cổng 80 trên Windows Firewall)." -ForegroundColor Cyan
if ($Tunnel) { Write-Host "Truy cập từ Internet (HTTPS): $PublicSite" -ForegroundColor Cyan }
