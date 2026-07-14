# TT-OMNIBUS leg 2 — M-yield TT driver (wave-tt-omnibus-plan.md rev 2.1 §1-2; protocol (b)).
# ONE detached Windows process runs the whole batch: 10 seeds x (1 h wall budget) of the
# FULL MIST pipeline (MistMain, demo profile + overrides: LLM-off, base.url PF 8080,
# canonical shipped registry target-triples.yaml, per-seed random.seed + experiment.name).
# The driver owns a self-healing gateway port-forward child (wsl kubectl) — the e2-run.sh
# same-shell lesson wrapped in a persistent process. Java Properties: LAST key wins, so the
# override block is APPENDED to a copy of the proven demo profile (no in-place edits).
$ErrorActionPreference = "Continue"
$repo = "C:\Users\miaot\Github\MIST"
Set-Location $repo
$cp = "mist-cli/target/classes;mist-core/target/classes;" + (Get-Content cp.txt -Raw).Trim()
$logdir = "debug/a-main/benchmark/b4/ttomni/leg2"
New-Item -ItemType Directory -Force $logdir | Out-Null
$driverLog = "$logdir/driver.log"
function Log($m) { "$(Get-Date -Format HH:mm:ss) $m" | Add-Content $driverLog }

function Ensure-PF {
    $up = Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $up) {
        Log "PF dead - restarting wsl kubectl port-forward 8080"
        Start-Process wsl -ArgumentList 'kubectl','--context','kind-mist','-n','trainticket','port-forward','svc/ts-ui-dashboard','8080:8080' -WindowStyle Hidden
        Start-Sleep 10
    }
}

Log "=== M-yield driver start (10 seeds x 1h, MIST pin = main_track HEAD at launch) ==="
foreach ($i in 0..9) {
    $seed = 20260714 + $i
    $props = "mist-cli/src/main/resources/My-Example/ttomni-myield-s$seed.properties"
    Copy-Item "mist-cli/src/main/resources/My-Example/trainticket-demo.properties" $props -Force
    @"

# ===== TT-OMNIBUS M-yield overrides (appended; Properties last-key-wins) =====
llm.enabled=false
base.url=http://localhost:8080
experiment.name=ttomni_myield_s$seed
random.seed=$seed
mst.oracle.dataintegrity.registry=target-triples.yaml
"@ | Add-Content $props
    Ensure-PF
    Log "seed $seed START (props=$props)"
    $p = Start-Process java -ArgumentList '-cp', $cp, 'io.mist.cli.MistMain', $props `
        -RedirectStandardOutput "$logdir/myield-s$seed.log" `
        -RedirectStandardError  "$logdir/myield-s$seed.err" `
        -WindowStyle Hidden -PassThru
    if (-not $p.WaitForExit(3600000)) {
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        Log "seed $seed KILLED at the 1h wall budget (pinned; by design)"
    } else {
        Log "seed $seed EXIT rc=$($p.ExitCode)"
    }
}
Log "=== M-yield driver DONE ==="
"done $(Get-Date -Format s)" | Set-Content "$logdir/DRIVER-DONE.txt"
