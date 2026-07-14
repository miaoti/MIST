# TT-OMNIBUS leg 3 — E5 ablations driver (wave-tt-omnibus-plan.md rev 2.1 §1-3; protocol (d)).
# The frozen exact-4 OAT matrix x 5 repetitions on the S1 cancel-refund pair. Config-only
# axes (source-verified): A1 oracle_mode (paired<->observe invocation), A2 trace-gate
# (jaeger.base.url set/absent), A3 quiescence cap (mst.oracle.dataintegrity.timeout.ms
# default 10000 -> 20000). Runners EXIST (TrainTicketStimulus paired; TtOmniObserveLeg
# observe); the fork is applied by set-image 1.0.5 at start (runtime toggle per leg inside
# the runners). Run AFTER the M-yield batch (exclusive-runs rail).
#   C0 paired/default        C1 observe + jaeger        C2 observe - jaeger
#   C3 paired/extended-cap (timeout.ms=20000)
$ErrorActionPreference = "Continue"
$repo = "C:\Users\miaot\Github\MIST"
Set-Location $repo
$cp = "mist-cli/target/classes;mist-core/target/classes;" + (Get-Content cp.txt -Raw).Trim()
$logdir = "debug/a-main/benchmark/b4/ttomni/leg3"
New-Item -ItemType Directory -Force $logdir | Out-Null
$driverLog = "$logdir/e5-driver.log"
function Log($m) { "$(Get-Date -Format HH:mm:ss) $m" | Add-Content $driverLog }

function Ensure-PF($port, $ns, $target) {
    $up = Test-NetConnection -ComputerName localhost -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue
    if (-not $up) {
        Log "PF $port dead - restarting ($ns/$target)"
        Start-Process wsl -ArgumentList 'kubectl','--context','kind-mist','-n',$ns,'port-forward',$target,"${port}:$(if($port -eq 16686){'16686'}else{'8080'})" -WindowStyle Hidden
        Start-Sleep 10
    }
}

function Run-Java($argList, $outFile) {
    $p = Start-Process java -ArgumentList $argList -RedirectStandardOutput $outFile `
        -RedirectStandardError "$outFile.err" -WindowStyle Hidden -PassThru
    if (-not $p.WaitForExit(1800000)) { Stop-Process -Id $p.Id -Force; Log "KILLED at 30min cap: $outFile" }
    else { Log "exit rc=$($p.ExitCode): $outFile" }
}

Log "=== E5 driver start (exact-4 OAT x 5 reps; fork set-image 1.0.5) ==="
wsl kubectl --context kind-mist -n trainticket set image deploy/ts-inside-payment-service ts-inside-payment-service=codewisdom/ts-inside-payment-service:1.0.5 | Add-Content $driverLog
wsl kubectl --context kind-mist -n trainticket rollout status deploy/ts-inside-payment-service --timeout=300s | Add-Content $driverLog

$jpod = (wsl kubectl --context kind-mist -n istio-system get pods -l app=jaeger -o name | Select-Object -First 1).Trim()
$pairedBase = @('-cp', $cp,
    '-Dg3.base.url=http://localhost:8080', '-Dg3.strata=constructed',
    '-Dg3.triples.natural=evaluation/suts/trainticket/g3/target-triples-natural.yaml',
    '-Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml',
    '-Dg3.contract.path=debug/a-main/g3-comparator-tt/assertion-bindings-cancel-refund.yaml')

foreach ($rep in 1..5) {
    Ensure-PF 8080 'trainticket' 'svc/ts-ui-dashboard'
    Ensure-PF 16686 'istio-system' $jpod
    # C0: paired / default cap
    Log "rep $rep C0 paired/default"
    Run-Java ($pairedBase + @('io.mist.cli.g3.TrainTicketStimulus')) "$logdir/C0-rep$rep.log"
    # C3: paired / extended cap (A3)
    Log "rep $rep C3 paired/timeout20000"
    Run-Java ($pairedBase + @('-Dmst.oracle.dataintegrity.timeout.ms=20000', 'io.mist.cli.g3.TrainTicketStimulus')) "$logdir/C3-rep$rep.log"
    # C1: observe + jaeger (A1+A2 reference) — control then fault leg, n=1 per rep
    foreach ($leg in @('control','fault')) {
        Log "rep $rep C1 observe+jaeger leg=$leg"
        Run-Java @('-cp', $cp,
            '-Dg3.base.url=http://localhost:8080',
            '-Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml',
            '-Djaeger.base.url=http://localhost:16686/jaeger/api',
            "-Dttomni.leg=$leg", '-Dttomni.n=1',
            'io.mist.cli.g3.TtOmniObserveLeg') "$logdir/C1-$leg-rep$rep.log"
    }
    # C2: observe - jaeger (A2 off)
    foreach ($leg in @('control','fault')) {
        Log "rep $rep C2 observe-nojaeger leg=$leg"
        Run-Java @('-cp', $cp,
            '-Dg3.base.url=http://localhost:8080',
            '-Dg3.triples.constructed=evaluation/suts/trainticket/g3/target-triples-constructed.yaml',
            "-Dttomni.leg=$leg", '-Dttomni.n=1',
            'io.mist.cli.g3.TtOmniObserveLeg') "$logdir/C2-$leg-rep$rep.log"
    }
}
Log "=== E5 driver DONE ==="
"done $(Get-Date -Format s)" | Set-Content "$logdir/E5-DONE.txt"
