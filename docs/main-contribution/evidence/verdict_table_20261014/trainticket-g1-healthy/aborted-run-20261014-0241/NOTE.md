# Aborted TrainTicket run of 2026-10-14 02:41 CDT (not a run of record)

MIST run `trainticket_g1_healthy` (run id 1791963686618, G1 probe profile, 5 s trace wait) started
at 02:41:18. Its generation-time pass executed 347 tests with traces by 03:00; the scan in this
folder covers the first 318 of them (34 tests answered 2xx, none with a failed span in its trace;
284 answered 4xx; the invariant fired on none). At 03:00 MIST started its execution phase and
cleared the Allure results directory, so the raw files of that first pass are gone.

The agent harness then stopped the session's background shells because the host was low on memory
(about 1.9 GB available of 32.4 GB, the WSL VM at 16 GB). That killed the Jaeger port-forward
(localhost:16686). The MIST JVM survived as an orphan, but from then on every test attached
"Jaeger Trace Error: java.net.ConnectException" instead of a trace, so the execution phase could not
produce verdicts. The JVM and the dashboard port-forward were stopped by hand at about 03:05, after
187 trace-less tests. No row of the verdict table comes from this run.
