# MYC per-SUT M-yield driver (wave-myield-completion rev 2; one detached instance per leg).
# Per seed: props = shipped/authored profile + APPENDED override block (last-key-wins);
# pre-seed HEALTH (home 200) + RAM (<3Gi avail => PAUSE) probes; java 1h wall cap;
# post-seed EVIDENCE COPY-OUT (allure-results + fault report + test-data) BEFORE the next
# seed's deletepreviousresults wipe; PAUSED seeds leave PAUSED-s<seed>.txt markers (the
# outcome table reports run/paused/total). SS extra: readback collection-size probe.
param(
    [Parameter(Mandatory)][string]$Sut,          # teastore|oteldemo|sockshop|bookinfo|boutique
    [Parameter(Mandatory)][string]$Props,        # path to the SUT profile (repo-relative)
    [Parameter(Mandatory)][string]$Ns,           # k8s namespace
    [Parameter(Mandatory)][string]$PfTarget,     # svc/<name>
    [Parameter(Mandatory)][int]$PfLocal,         # local PF port
    [Parameter(Mandatory)][int]$PfRemote,        # remote svc port
    [Parameter(Mandatory)][string]$HealthPath,   # e.g. /tools.descartes.teastore.webui/
    [Parameter(Mandatory)][int]$Seeds,           # 10 or 3
    [string]$SsProbeUrl = ""                     # SS only: readback collection probe URL
)
$ErrorActionPreference = "Continue"
$repo = "C:\Users\miaot\Github\MIST"
Set-Location $repo
$cp = "mist-cli/target/classes;mist-core/target/classes;" + (Get-Content cp.txt -Raw).Trim()
$logdir = "debug/a-main/benchmark/b4/ttomni/myc/$Sut"
New-Item -ItemType Directory -Force $logdir | Out-Null
$driverLog = "$logdir/driver.log"
function Log($m) { "$(Get-Date -Format HH:mm:ss) $m" | Add-Content $driverLog }

function Ensure-PF {
    $up = Test-NetConnection -ComputerName localhost -Port $PfLocal -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $up) {
        Log "PF $PfLocal dead - restarting ($Ns/$PfTarget)"
        Start-Process wsl -ArgumentList 'kubectl','--context','kind-mist','-n',$Ns,'port-forward',$PfTarget,"${PfLocal}:${PfRemote}" -WindowStyle Hidden
        Start-Sleep 10
    }
}

Log "=== MYC driver start: $Sut x $Seeds seeds (wave pin = main_track HEAD at leg start) ==="
foreach ($i in 0..($Seeds-1)) {
    $seed = 20260714 + $i
    Ensure-PF
    # pre-seed probes
    try { $h = (Invoke-WebRequest -Uri "http://localhost:$PfLocal$HealthPath" -UseBasicParsing -TimeoutSec 20).StatusCode } catch { $h = 0 }
    $ramGi = [math]::Round((wsl -e bash -c "free -g | awk '/^Mem:/{print `$7}'" | Out-String).Trim())
    Log "seed $seed pre-probe: health=$h ramAvailGi=$ramGi"
    if ($h -ne 200 -or $ramGi -lt 3) {
        "health=$h ramAvailGi=$ramGi at $(Get-Date -Format s)" | Set-Content "$logdir/PAUSED-s$seed.txt"
        Log "seed $seed PAUSED (probe fail) — remaining seeds of this SUT paused too"
        foreach ($j in ($i+1)..($Seeds-1)) { "cascade-paused after s$seed" | Set-Content "$logdir/PAUSED-s$(20260714+$j).txt" }
        break
    }
    if ($SsProbeUrl -ne "") {
        try { $r = Invoke-WebRequest -Uri $SsProbeUrl -UseBasicParsing -TimeoutSec 15; $len = $r.Content.Length } catch { $len = -1 }
        Log "seed $seed SS collection probe: bytes=$len (readback_bound saturation watch)"
    }
    # per-seed props
    $sp = "$logdir/props-s$seed.properties"
    Copy-Item $Props $sp -Force
    @"

# ===== MYC override block (appended; Properties last-key-wins; plan rev 2 SS2) =====
llm.enabled=false
jaeger.enabled=false
base.url=http://localhost:$PfLocal
experiment.name=myc_${Sut}_s$seed
random.seed=$seed
"@ | Add-Content $sp
    Log "seed $seed START"
    $p = Start-Process java -ArgumentList '-cp', $cp, 'io.mist.cli.MistMain', $sp `
        -RedirectStandardOutput "$logdir/myc-s$seed.log" -RedirectStandardError "$logdir/myc-s$seed.err" `
        -WindowStyle Hidden -PassThru
    if (-not $p.WaitForExit(3600000)) {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        Log "seed $seed KILLED at the 1h wall budget (by design)"
    } else { Log "seed $seed EXIT rc=$($p.ExitCode)" }
    # evidence copy-out BEFORE the next seed's wipe
    $ev = "$logdir/s$seed"
    New-Item -ItemType Directory -Force $ev | Out-Null
    if (Test-Path target/allure-results) { Copy-Item target/allure-results "$ev/allure-results" -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path target/test-data)     { Copy-Item target/test-data "$ev/test-data" -Recurse -Force -ErrorAction SilentlyContinue }
    Get-ChildItem logs/fault-detection-reports -Filter "*myc_${Sut}_s$seed*" -ErrorAction SilentlyContinue | Copy-Item -Destination $ev -Force
    Log "seed $seed evidence copied -> $ev"
}
Log "=== MYC driver DONE: $Sut ==="
"done $(Get-Date -Format s)" | Set-Content "$logdir/DRIVER-DONE.txt"
