# Round 11 re-run driver: one MIST leg, detached from the agent's shells.
# usage: powershell -File run_leg.ps1 -Sut bookinfo -Leg bk-nooutage -Profile <abs .properties>
#        -WaitMs 0 -OutDir <dir> -SutPort 8080 -SutNs istio-system -SutSvc istio-ingressgateway
#        -Probes "/api/v1/products/0/reviews","/api/v1/products/0/ratings"
param(
  [string]$Sut, [string]$Leg, [string]$Profile, [int]$WaitMs = 0, [string]$OutDir,
  [int]$SutPort, [string]$SutNs, [string]$SutSvc, [string]$Probes
)
$ErrorActionPreference = "Continue"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$log = Join-Path $OutDir "driver.log"
function Log($m) { Add-Content -Path $log -Value ((Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " " + $m) }
function Listening($port) { return [bool](Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) }

Log "leg $Leg start; profile $Profile; wait $WaitMs ms"
$started = @()
if (-not (Listening 16686)) {
  $started += Start-Process -FilePath "wsl.exe" -ArgumentList "-d","Ubuntu","--","kubectl","port-forward","-n","istio-system","svc/tracing","16686:80" -WindowStyle Hidden -PassThru
  Log "started Jaeger port-forward"
} else { Log "Jaeger port 16686 already listening" }
if (-not (Listening $SutPort)) {
  $started += Start-Process -FilePath "wsl.exe" -ArgumentList "-d","Ubuntu","--","kubectl","port-forward","-n",$SutNs,"svc/$SutSvc","$($SutPort):80" -WindowStyle Hidden -PassThru
  Log "started SUT port-forward on $SutPort"
} else { Log "SUT port $SutPort already listening" }
Start-Sleep -Seconds 8

try { $r = Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:16686/jaeger/api/services" -TimeoutSec 20; Log "probe jaeger $($r.StatusCode)" }
catch { Log "probe jaeger failed: $($_.Exception.Message)" }
foreach ($p in ($Probes -split ',')) {
  try { $r = Invoke-WebRequest -UseBasicParsing -Uri ("http://localhost:" + $SutPort + $p) -TimeoutSec 20; Log "probe $p $($r.StatusCode)" }
  catch { $c = $null; if ($_.Exception.Response) { $c = [int]$_.Exception.Response.StatusCode }; Log "probe $p $c $($_.Exception.Message)" }
}

$rt = "C:\Users\miaot\Github\MIST\evaluation\suts\$Sut\.runtime"
$ar = Join-Path $rt "target\allure-results"
if (Test-Path $ar) {
  $leaf = "allure-results-before-$Leg-" + (Get-Date -Format "HHmmss")
  Rename-Item -Path $ar -NewName $leaf
  Log "moved previous results to target\$leaf"
}

$jar = "C:\Users\miaot\Github\MIST\mist-cli\target\mist.jar"
$t0 = Get-Date
$proc = Start-Process -FilePath "java" -ArgumentList "-Dmst.test.jaeger.propagation.delay.ms=$WaitMs","-jar",$jar,$Profile `
  -WorkingDirectory $rt -RedirectStandardOutput (Join-Path $OutDir "mist.log") -RedirectStandardError (Join-Path $OutDir "mist.err") `
  -NoNewWindow -PassThru -Wait
$mins = [math]::Round(((Get-Date) - $t0).TotalMinutes, 1)
Log "mist exit $($proc.ExitCode); $mins minutes"

if (Test-Path $ar) {
  Copy-Item -Recurse -Path $ar -Destination (Join-Path $OutDir "allure-results")
  Log ("copied " + (Get-ChildItem $ar).Count + " result files")
}
$fd = Join-Path $rt "logs\fault-detection-reports"
if (Test-Path $fd) {
  Get-ChildItem $fd -File | Where-Object { $_.LastWriteTime -gt $t0 } | ForEach-Object { Copy-Item $_.FullName $OutDir; Log "copied $($_.Name)" }
}
foreach ($s in $started) { try { Stop-Process -Id $s.Id -Force -ErrorAction Stop; Log "stopped wsl.exe $($s.Id)" } catch { Log "stop $($s.Id): $($_.Exception.Message)" } }
Set-Content -Path (Join-Path $OutDir "DONE") -Value ("exit=" + $proc.ExitCode + " minutes=" + $mins)
Log "leg $Leg done"
