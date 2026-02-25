# ─────────────────────────────────────────────────────────────────────────────
# Project Nidhi — Demo Startup Script
# Runs on the presenter's laptop. Sets up firewall, (optionally) creates a
# Windows Mobile Hotspot, then starts all 3 services and shows the connection
# URL + QR link so phones can join instantly.
# ─────────────────────────────────────────────────────────────────────────────
param(
    [switch]$Hotspot,       # Pass -Hotspot to enable Windows Mobile Hotspot
    [switch]$SkipFirewall,  # Pass -SkipFirewall to skip firewall rule creation
    [string]$GoogleApiKey   # Pass -GoogleApiKey YOUR_KEY (or set env var beforehand)
)

$ErrorActionPreference = "Continue"
$ROOT    = Split-Path -Parent $PSScriptRoot
$MAVEN   = "C:\tools\apache-maven-3.9.9\bin\mvn.cmd"
$BACKEND = "$ROOT\nidhi-backend"
$BANK    = "$ROOT\nidhi-bank"
$SIDECAR = "$ROOT\nidhi-backend\insect_sidecar"

# ── 1. Google API key ────────────────────────────────────────────────────────
if ($GoogleApiKey) {
    $env:GOOGLE_API_KEY = $GoogleApiKey
    Write-Host "[KEY] Google API key set from parameter." -ForegroundColor Green
} elseif (-not $env:GOOGLE_API_KEY) {
    $localProps = "$BACKEND\src\main\resources\application-local.properties"
    if (Test-Path $localProps) {
        $keyLine = Get-Content $localProps | Where-Object { $_ -match "google\.api\.key\s*=" }
        if ($keyLine) {
            $env:GOOGLE_API_KEY = ($keyLine -split "=", 2)[1].Trim()
            Write-Host "[KEY] Google API key loaded from application-local.properties." -ForegroundColor Green
        }
    } else {
        Write-Warning "[KEY] No Google API key found. Voice transcription will fail. Set `$env:GOOGLE_API_KEY or pass -GoogleApiKey."
    }
}

# ── 2. Detect local IP ───────────────────────────────────────────────────────
$localIp = (
    Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notmatch "^127\." -and $_.PrefixOrigin -ne "WellKnown" } |
    Sort-Object { if ($_.IPAddress -match "^192\.168\.137\.") { 0 } elseif ($_.IPAddress -match "^192\.168\.") { 1 } else { 2 } } |
    Select-Object -First 1
).IPAddress

if (-not $localIp) { $localIp = "127.0.0.1" }

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║             PROJECT NIDHI — DEMO LAUNCHER                   ║" -ForegroundColor Cyan
Write-Host "╠══════════════════════════════════════════════════════════════╣" -ForegroundColor Cyan
Write-Host "║  Local IP  : $($localIp.PadRight(48)) ║" -ForegroundColor Cyan
Write-Host "║  Backend   : http://$($localIp):8081$(' ' * (37 - $localIp.Length)) ║" -ForegroundColor Cyan
Write-Host "║  Bank Admin: http://$($localIp):8082/admin.html$(' ' * (28 - $localIp.Length)) ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 3. Firewall rules ────────────────────────────────────────────────────────
if (-not $SkipFirewall) {
    Write-Host "[FIREWALL] Opening ports 8081 and 8082..." -ForegroundColor Yellow
    $rules = Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like "Nidhi*" }
    if (-not $rules) {
        try {
            New-NetFirewallRule -DisplayName "Nidhi Backend (8081)" `
                -Direction Inbound -Protocol TCP -LocalPort 8081 `
                -Action Allow -Profile Any -ErrorAction Stop | Out-Null
            New-NetFirewallRule -DisplayName "Nidhi Bank (8082)" `
                -Direction Inbound -Protocol TCP -LocalPort 8082 `
                -Action Allow -Profile Any -ErrorAction Stop | Out-Null
            Write-Host "[FIREWALL] Rules created." -ForegroundColor Green
        } catch {
            Write-Warning "[FIREWALL] Could not create rules (try running as Administrator): $_"
        }
    } else {
        Write-Host "[FIREWALL] Rules already exist." -ForegroundColor Green
    }
}

# ── 4. Windows Mobile Hotspot ────────────────────────────────────────────────
if ($Hotspot) {
    Write-Host "[HOTSPOT] Enabling Windows Mobile Hotspot..." -ForegroundColor Yellow
    try {
        $connectionProfile = [Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity,ContentType=WindowsRuntime]::GetInternetConnectionProfile()
        $tetheringMgr = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager,Windows.Networking.NetworkOperators,ContentType=WindowsRuntime]::CreateFromConnectionProfile($connectionProfile)
        if ($tetheringMgr.TetheringOperationalState -ne "On") {
            $tetheringMgr.StartTetheringAsync() | Out-Null
            Start-Sleep -Seconds 3
            Write-Host "[HOTSPOT] Mobile Hotspot enabled. Phone should connect to this laptop's hotspot." -ForegroundColor Green
        } else {
            Write-Host "[HOTSPOT] Mobile Hotspot is already active." -ForegroundColor Green
        }
    } catch {
        Write-Warning "[HOTSPOT] Could not auto-enable. Please enable manually: Settings -> Network -> Mobile Hotspot"
    }
}

# ── 5. Check Maven ───────────────────────────────────────────────────────────
if (-not (Test-Path $MAVEN)) {
    $MAVEN = "mvn"   # fall back to PATH
}
$env:PATH += ";C:\tools\apache-maven-3.9.9\bin"

# ── 6. Start Python sidecar (background) ────────────────────────────────────
Write-Host "[SIDECAR] Starting Python insect-entropy sidecar..." -ForegroundColor Yellow
$sidecarJob = Start-Process -FilePath "python" -ArgumentList "main.py" `
    -WorkingDirectory $SIDECAR -WindowStyle Minimized -PassThru
Write-Host "[SIDECAR] PID $($sidecarJob.Id)" -ForegroundColor Green

Start-Sleep -Seconds 2

# ── 7. Start nidhi-backend (background terminal) ────────────────────────────
Write-Host "[BACKEND] Starting nidhi-backend on port 8081..." -ForegroundColor Yellow
$backendJob = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k", "title Nidhi-Backend && cd /d $BACKEND && $MAVEN spring-boot:run" `
    -WindowStyle Normal -PassThru
Write-Host "[BACKEND] Window opened (PID $($backendJob.Id)). Waiting for startup..." -ForegroundColor Green

Start-Sleep -Seconds 8

# ── 8. Start nidhi-bank (background terminal) ───────────────────────────────
Write-Host "[BANK]    Starting nidhi-bank on port 8082..." -ForegroundColor Yellow
$bankJob = Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k", "title Nidhi-Bank && cd /d $BANK && $MAVEN spring-boot:run" `
    -WindowStyle Normal -PassThru
Write-Host "[BANK]    Window opened (PID $($bankJob.Id))." -ForegroundColor Green

Start-Sleep -Seconds 5

# ── 9. Open admin dashboard in browser ──────────────────────────────────────
$adminUrl = "http://$($localIp):8082/admin.html"
Write-Host ""
Write-Host "[BROWSER] Opening admin dashboard: $adminUrl" -ForegroundColor Cyan
Start-Process $adminUrl

# ── 10. Summary ──────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "════════════════════════════ READY ════════════════════════════" -ForegroundColor Green
Write-Host "  Admin Dashboard : $adminUrl" -ForegroundColor White
Write-Host "  Backend API     : http://$($localIp):8081" -ForegroundColor White
Write-Host ""
Write-Host "  On the phone:" -ForegroundColor Yellow
Write-Host "    1. Connect to laptop hotspot (or same WiFi)" -ForegroundColor White
Write-Host "    2. Open Nidhi app -> menu -> Set Server URL" -ForegroundColor White
Write-Host "    3. Tap 'Scan QR' and scan the code from the admin dashboard" -ForegroundColor White
Write-Host "    4. Start transacting!" -ForegroundColor White
Write-Host "════════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "Press Ctrl+C to stop this script (servers keep running in their windows)."
