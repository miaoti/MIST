# E2E ALLURE DEMO — the full MIST pipeline, end to end, ONE run of record:
# MistMain (gate1-pairing profile base = the PROVEN G1 run-3 FIRE configuration) generates the
# TT suite and EXECUTES it observe-mode with the data-integrity oracle armed, against TT with
# the adminroute lost-write fault ON (e2e-demo-prep.sh) and the write path instrumented, so the
# acked-but-lost create is caught live (OBSERVED_COMPLETE_ABSENT) and — with
# mst.oracle.dataintegrity.failonlost=true — FAILS the generated test with the
# "ACKED-BUT-LOST WRITE" marker that the Allure category keys on. The allure-results of this
# run are then rendered to the HTML report artifact.
$ErrorActionPreference = "Continue"
$repo = "C:\Users\miaot\Github\MIST"
Set-Location $repo
$cp = "mist-cli/target/classes;mist-core/target/classes;" + (Get-Content mist-cli/cp.txt -Raw).Trim()
$outdir = "debug/a-main/benchmark/b4/e2e-demo"
New-Item -ItemType Directory -Force $outdir | Out-Null

$props = "mist-cli/src/main/resources/My-Example/tt-e2e-allure-demo.properties"
Copy-Item "mist-cli/src/main/resources/My-Example/trainticket-gate1-pairing.properties" $props -Force
@"

# ===== E2E ALLURE DEMO overrides (appended; Properties last-key-wins) =====
llm.enabled=false
base.url=http://localhost:8080
experiment.name=tt_e2e_allure_demo
testclass.name=TtE2eAllureDemo
test.target.package=tt_e2e_allure_demo
test.variants.per.scenario=5
allure.report=true
# OBSERVE mode (the Allure-rendering path): the gate1 base turns the paired
# injector on; force it OFF so MistRunner takes the single-leg observe branch
# (the SUT-side fault is already ON via e2e-demo-prep.sh).
mist.fault.injection.enabled=false
mst.oracle.dataintegrity.enabled=true
mst.oracle.dataintegrity.registry=target-triples.yaml
mst.oracle.dataintegrity.failonlost=true
jaeger.base.url=http://localhost:30005/jaeger/ui/api
"@ | Add-Content $props

"$(Get-Date -Format s) demo run START" | Set-Content "$outdir/driver.log"
$p = Start-Process java -ArgumentList '-cp', $cp, 'io.mist.cli.MistMain', $props `
    -RedirectStandardOutput "$outdir/run.log" `
    -RedirectStandardError  "$outdir/run.err" `
    -WindowStyle Hidden -PassThru
if (-not $p.WaitForExit(2400000)) {
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    "$(Get-Date -Format s) KILLED at the 40min wall" | Add-Content "$outdir/driver.log"
} else {
    "$(Get-Date -Format s) EXIT rc=$($p.ExitCode)" | Add-Content "$outdir/driver.log"
}
"done" | Set-Content "$outdir/DRIVER-DONE.txt"
