<#
  Dừng toàn bộ ShopVi do start-server.ps1 khởi động (theo thứ tự ngược lại).
  Dùng: powershell -ExecutionPolicy Bypass -File deploy\windows\stop-server.ps1
#>
param(
    [string]$Infra = (Join-Path $HOME '.shopvi-infra')
)
$Pids = Join-Path $Infra 'pids'

$Nginx = Get-ChildItem (Join-Path $Infra 'nginx-*') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if ($Nginx -and (Test-Path (Join-Path $Nginx 'logs\nginx.pid'))) {
    & (Join-Path $Nginx 'nginx.exe') -p $Nginx -c conf/shopvi-lan.conf -s quit 2>$null
}

# Dừng tiến trình và mọi tiến trình con (elasticsearch.bat → java, ...)
function Stop-Tree([int]$ProcessId) {
    Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Tree $_.ProcessId }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

foreach ($name in 'tunnel', 'nginx', 'frontend', 'backend', 'kafka', 'elasticsearch', 'minio', 'redis') {
    $file = Join-Path $Pids "$name.pid"
    if (Test-Path $file) {
        Stop-Tree ([int](Get-Content $file))
        Remove-Item $file
        Write-Host "  dừng $name"
    }
}

Remove-Item (Join-Path $Infra 'tunnel-url.txt') -ErrorAction SilentlyContinue

$PgCtl = Join-Path $Infra 'pgsql\bin\pg_ctl.exe'
if (Test-Path $PgCtl) {
    & $PgCtl -D (Join-Path $Infra 'pgdata') stop -m fast 2>$null | Out-Null
    Write-Host '  dừng postgres'
}
