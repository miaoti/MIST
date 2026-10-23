# Round 11c Online Boutique legs (form-body fix), detached: no outage, then adservice scaled to zero, then the
# whole namespace back to zero (its resting state). The load generator must already be at zero.
param([string]$Root)  # directory for the legs' outputs and the sequence log
$ErrorActionPreference = "Continue"
$root = $Root
$log = Join-Path $root "boutique-c-seq.log"
function Log($m) { Add-Content -Path $log -Value ((Get-Date -Format "yyyy-MM-dd HH:mm:ss") + " " + $m) }
function Kube([string[]]$a) { $o = & wsl.exe -d Ubuntu -- kubectl @a 2>&1 | Out-String; Log ("kubectl " + ($a -join " ") + " -> " + $o.Trim()) }
$driver = "C:\Users\miaot\Github\MIST\docs\main-contribution\evidence\verdict_table_20261015\run_leg.ps1"
$profile = "C:\Users\miaot\Github\MIST\evaluation\suts\boutique\boutique-hdf-r11c.properties"
$probes = "/,/product/OLJCESPC7Z"

Log "sequence start"
Kube @("get", "deploy", "-n", "boutique", "--no-headers")
& powershell.exe -NoProfile -File $driver -Sut boutique -Leg bq-c-nooutage -Profile $profile -WaitMs 5000 `
  -OutDir (Join-Path $root "bq-c-nooutage") -SutPort 8081 -SutNs boutique -SutSvc frontend -Probes $probes
Log "no-outage leg returned"

Kube @("scale", "deploy/adservice", "-n", "boutique", "--replicas=0")
Kube @("wait", "--for=delete", "pod", "-l", "app=adservice", "-n", "boutique", "--timeout=180s")
Start-Sleep -Seconds 10
Kube @("get", "pods", "-n", "boutique", "-l", "app=adservice", "--no-headers")
& powershell.exe -NoProfile -File $driver -Sut boutique -Leg bq-c-outage -Profile $profile -WaitMs 5000 `
  -OutDir (Join-Path $root "bq-c-outage") -SutPort 8081 -SutNs boutique -SutSvc frontend -Probes $probes
Log "outage leg returned"

Kube @("scale", "deploy", "--all", "-n", "boutique", "--replicas=0")
Log "sequence done"
Set-Content -Path (Join-Path $root "BOUTIQUE-C-DONE") -Value "done"
