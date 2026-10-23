# Round 11b Bookinfo legs, detached: no outage, then ratings scaled to zero, then ratings restored.
param([string]$Root)  # directory for the legs' outputs and the sequence log
$ErrorActionPreference = "Continue"
$root = $Root
$log = Join-Path $root "bookinfo-seq.log"
function Log($m) { Add-Content -Path $log -Value ((Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " " + $m) }
function Kube([string[]]$a) { $o = & wsl.exe -d Ubuntu -- kubectl @a 2>&1; Log ("kubectl " + ($a -join " ") + " -> " + (($o | Out-String).Trim())) }
$driver = "C:\Users\miaot\Github\MIST\docs\main-contribution\evidence\verdict_table_20261015\run_leg.ps1"
$profile = "C:\Users\miaot\Github\MIST\evaluation\suts\bookinfo\bookinfo-hdf-r11b.properties"
$probes = "/api/v1/products/0/reviews,/api/v1/products/0/ratings"

Log "sequence start"
Kube @("get", "deploy", "ratings-v1", "-n", "default", "--no-headers")
& powershell.exe -NoProfile -File $driver -Sut bookinfo -Leg bk-nooutage -Profile $profile -WaitMs 10000 `
  -OutDir (Join-Path $root "bk-nooutage") -SutPort 8080 -SutNs istio-system -SutSvc istio-ingressgateway -Probes $probes
Log "no-outage leg returned"

Kube @("scale", "deploy/ratings-v1", "-n", "default", "--replicas=0")
Kube @("wait", "--for=delete", "pod", "-l", "app=ratings", "-n", "default", "--timeout=180s")
Start-Sleep -Seconds 10
Kube @("get", "pods", "-n", "default", "-l", "app=ratings", "--no-headers")
& powershell.exe -NoProfile -File $driver -Sut bookinfo -Leg bk-outage -Profile $profile -WaitMs 10000 `
  -OutDir (Join-Path $root "bk-outage") -SutPort 8080 -SutNs istio-system -SutSvc istio-ingressgateway -Probes $probes
Log "outage leg returned"

Kube @("scale", "deploy/ratings-v1", "-n", "default", "--replicas=1")
Kube @("rollout", "status", "deploy/ratings-v1", "-n", "default", "--timeout=300s")
Log "sequence done"
Set-Content -Path (Join-Path $root "BOOKINFO-DONE") -Value "done"
