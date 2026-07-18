#!/bin/bash
# The E2E Allure demo run of record: ONE observe session; write #1 lands (validates the
# read-back per U8), the fault flips ON mid-session (rollout), write #2 is acked-but-LOST
# -> the generated test FAILS with the ACKED-BUT-LOST marker -> Allure report.
set -u
cd /c/Users/miaot/Github/MIST
CP="mist-cli/target/classes;mist-cli/target/test-classes;mist-core/target/classes;$(cat mist-cli/cp.txt)"
RES=debug/a-main/benchmark/b4/e2e-demo/allure-results
rm -rf "$RES"

# Phase 0: fault OFF baseline (agent kept) so write #1 lands and validates the read-back (U8).
wsl -e bash -c "sed -i 's/\r\$//' /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/fcorpus/e2e-demo-reset.sh && bash /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/fcorpus/e2e-demo-reset.sh"

# NOTE: TinyObserveRunner's ProcessBuilder("bash") resolves to System32's WSL bash
# on this box — the exec command below therefore runs INSIDE WSL directly.
FLIP="bash /mnt/c/Users/miaot/Github/MIST/debug/a-main/benchmark/b4/runners/fcorpus/e2e-demo-flip.sh"

java -cp "$CP" \
  -Dauth.mode=per_jvm \
  -Dmst.oracle.shape.enabled=false \
  -Dmst.oracle.dataintegrity.failonlost=true \
  -Djaeger.base.url="http://localhost:30005/jaeger/api" \
  -Dallure.results.directory="$RES" \
  io.mist.cli.tools.TinyObserveRunner \
  "mist-cli/src/main/resources/My-Example/trainticket/target-triples.yaml" \
  "e2e-allure-demo" \
  "tt_e2e_allure_demo.TtE2eAllureDemo_1784410269399_phaseB.Flow_Scenario_15" \
  "exec:$FLIP" \
  "tt_e2e_allure_demo.TtE2eAllureDemo_1784410269399_phaseB.Flow_Scenario_15"
