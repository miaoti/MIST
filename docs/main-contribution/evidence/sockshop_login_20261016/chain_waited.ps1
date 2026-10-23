# Round 16, unattended: after the wait-0 catalogue-down leg (E2) finishes, run the two legs of record with a
# five-second trace wait (as the Online Boutique rows): E1w logged in, no outage; E2w logged in, catalogue
# down. Then scale every Sock Shop deployment back to zero and stop the port-forwards this session started.
$ErrorActionPreference = "Continue"
$base = "<run dir>"
$log = Join-Path $base "chain.log"
function Log($m) { Add-Content -Path $log -Value ((Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " " + $m) }
function Wsl($script) { $o = & wsl.exe -d Ubuntu -- bash $script 2>&1; Log ("wsl " + $script + ": " + (($o | Select-Object -Last 3) -join " | ")) }
function Probe($path) {
  try { $r = Invoke-WebRequest -UseBasicParsing -Uri ("http://localhost:8080" + $path) -TimeoutSec 20
        $t = if ($r.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
        Log ("probe " + $path + " " + $r.StatusCode + " " + $t.Substring(0, [math]::Min(60, $t.Length))) }
  catch { Log ("probe " + $path + " failed: " + $_.Exception.Message) }
}
$runLeg = "C:\Users\miaot\Github\MIST\docs\main-contribution\evidence\verdict_table_20261015\run_leg.ps1"
$prof = "C:\Users\miaot\Github\MIST\evaluation\suts\sockshop\sockshop-hdf-login.properties"
$wslDir = "<run dir in WSL>"

Log "chain start; waiting for E2 (wait 0) to finish"
while (-not (Test-Path (Join-Path $base "e2-login-catalogue-down\DONE"))) { Start-Sleep -Seconds 30 }
Log "E2 done"

# E1w: catalogue back up, logged in, no outage, 5 s wait
Wsl "$wslDir/ss_catup.sh"
Start-Sleep -Seconds 10
Probe "/catalogue?size=1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $runLeg -Sut sockshop -Leg ss-login-nooutage-wait5s -Profile $prof `
  -WaitMs 5000 -OutDir (Join-Path $base "e1w-login-nooutage-wait5s") -SutPort 8080 -SutNs sock-shop -SutSvc front-end -Probes "/catalogue?size=1,/tags"
Log "E1w done"

# E2w: catalogue down, logged in, 5 s wait
Wsl "$wslDir/ss_catdown.sh"
Start-Sleep -Seconds 10
Probe "/catalogue?size=1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $runLeg -Sut sockshop -Leg ss-login-catalogue-down-wait5s -Profile $prof `
  -WaitMs 5000 -OutDir (Join-Path $base "e2w-login-catalogue-down-wait5s") -SutPort 8080 -SutNs sock-shop -SutSvc front-end -Probes "/catalogue?size=1,/tags"
Log "E2w done"

Wsl "$wslDir/ss_down_all.sh"
$pf = Get-Content (Join-Path $base "pf_pids.txt") -ErrorAction SilentlyContinue
foreach ($id in ($pf -split ",")) { if ($id) { try { Stop-Process -Id ([int]$id) -Force -ErrorAction Stop; Log "stopped pf $id" } catch { Log "stop pf $id : $($_.Exception.Message)" } } }
Set-Content -Path (Join-Path $base "CHAIN_DONE") -Value (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Log "chain done"
