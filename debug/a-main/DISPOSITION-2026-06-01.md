# Roadmap Reviewer — 逐条 Disposition(2026-06-01)

> 被审:`ROADMAP-EXEC.md`。Reviewer = 对抗 agent(a99312bd)。本文件 = 每条 accept/partial/reject + grep 取证(遵循 `feedback_reviewer_disposition`)。
> **关键背景纠正(grep 坐实)**:存在**两个论文版本**。worktree `paper/main_issta.tex`(HEAD 33d872b4)= honest 版:4 SUT(:115/:133)、`\section{Design}`(:165)、**无 baseline 表**、诚实 outage caveat(:248);grep `one SUT|companion work` 在 worktree 版**0 命中**。主 checkout `paper/main_issta.tex` = 旧版:`Core Innovations`(:163)、10/10-vs-3/10-vs-6/10 表(:235)、`one SUT`(:245)。我的 `VERDICT §6` 审的是**旧版**,故多数硬伤其实 honest 版已修。

| # | Reviewer 评论 | Disposition | 依据 / 取证 |
|---|---|---|---|
| C1 | T1 缺一个隐藏依赖:"body oracle 漏检率"需要一个**真能 FAIL** 的 body 基线,而 MIST 自带的 ResponseEnvelope 是 no-op,用它会把漏检率虚抬到 100%(同义反复) | **ACCEPT** | `ResponseEnvelopeInvariant.java:130/118` failureSet 恒空、`:72` 唯一 FAIL 路径死、`:77` LLM 是 TODB。→ roadmap 加显式依赖:T1 漏检率必须用**外部/真实 body 基线**(LogiAgent/schema),不得用 MIST 自家 ResponseEnvelope。 |
| C2 | prevalence 只有 metric 无 design:缺 ground-truth(良性降级 ERROR vs 真失败,detector 分不清)、缺基线 oracle 套件、缺统计严谨、per-request vs per-endpoint | **ACCEPT(承重修改)** | `HiddenDownstreamFailureInvariant.java:80-82` 把任何后代 `http≥500∥otel=ERROR` 判 hidden;但 OB adservice 的 otel=ERROR 是**设计内优雅降级**(`boutique_e2e_pipeline.md:42-49`)→ 无独立 ground-truth 时"hidden failure 普遍性"塌成"swallowed-error-span 普遍性"。→ T1 从 metric 升级为 design:定 ground-truth 标注法 + 基线套件(status/schema/LLM-body)+ 采样框架/CI + 区分 per-request/per-endpoint。 |
| C3 | "机制 trivial"下面藏更糟的:**论文把 ResponseEnvelope 误述为 soft-error 决定信号 + one-shot LLM**,而代码里它是死的;最可能致命的不是"平凡"而是"你描述的 oracle 在 artifact 里不存在" | **ACCEPT(integrity-critical,最高优先)** | honest 版仍在:`:138`"ResponseEnvelope...catches the failure"、`:201`"one-shot LLM call on the first 2xx"、`:218`"decisive signal"、Fig1 `:193`。代码反证同 C1。真实检出是 marker(`MultiServiceRESTAssuredWriter.java:2181` `faultName`)。→ T3 必加任务:删/改 ResponseEnvelope 的 LLM+决定信号叙述,soft-error 归因改诚实;并在 A-main 同步。 |
| C4 venue | T3 该先 gate"窗口是否开"——repo README 说 ISSTA-2026 已关、ICSE-2027 才是 live | **PARTIAL(原则 accept,事实已解决)** | **web 核实(conf.researchr.org/track/splash-issta-2026/...):Tool Demonstrations 截止 2026-06-26 AoE、notif 7/24、camera 8/7 = 开着。** README(5/19 写)**过时/误**,需更正。→ Phase 0 第一 gate 仍保留"确认窗口",但事实判定=**开**。 |
| C4 frankenstein | worktree HEAD 部分回退 honest rewrite,abstract 4-SUT 但 title=Core Innovations、limitations=one SUT,自相矛盾 | **REJECT(过度断言,grep 反证)** | worktree 论文:title `:165 \section{Design}`(非 Core Innovations)、limitations `:248` 明写"four SUTs...shown on two"、`one SUT` **0 命中**。Reviewer 把**主 checkout** 的行号(163/245)当成 worktree。矛盾在**两 checkout 之间**,非 worktree 内部。→ 真动作:把 honest worktree 版**定为 canonical 并同步覆盖主 checkout 旧版**。 |
| C4 already-fixed | 33899439 已修 §6 大半(删 baseline 表、计数改 15036/6.5h、4 SUT) | **ACCEPT** | worktree 版 grep `RESTest~\cite|3/10|6/10|25\,h` 仅命中 ISBN 误配;`:218` 计数 15036/~6.5h,且有 artifact `debug/negative_test/runs/run22-fault-detection-10of10.txt`。→ 纠正 VERDICT §6:多数硬伤 honest 版已修,余 ResponseEnvelope+Fig1。 |
| C4b | 跨论文一致性风险:ResponseEnvelope 在 ISSTA 被吹、在 A-main 要撤;SUT 数/成熟度;attribution framing。无一致性管控步骤 | **ACCEPT** | 同 C3。→ roadmap 加"共享 claim 单一真相源 + 改一处镜像另一处"的一致性 checklist。注:若 C3 在两篇都修,ResponseEnvelope 这条不一致自动消。 |
| C5 T4 | attribution value-match 正确降级 | **ACCEPT(无需改)** | 与 `probe-attribution §4-5` 一致;保留 service-level 默认。 |
| C5 T2 | input-driven masking 被**低估**:outage-driven 不是化妆品 caveat,而是改变"论文在测什么"(SUT 韧性 vs 可测缺陷);spike"找不到就 future work"太随意 | **ACCEPT** | 全部 G2 证据均 outage-driven(`bookinfo_e2e_pipeline.md:111`、`boutique_e2e_pipeline.md:52`)。→ roadmap 把 outage-driven 从"风险表一行"升为**framing 决策**;T2 预先承诺"若失败如何 reframe",且 abstract 不得暗示 input-driven。 |
| C6 | 缺顶会必问项:**RCA/trace-anomaly 基线(TraceRCA/Nezha/RCAEval)**、RESTifAI prior-art delta、ToV、可复现性打包、AGORA+ 不可迁移性的 rebuttal 要进正文 | **ACCEPT** | Nezha/RCAEval 都在 OB+TT 跑 trace(`probe-wildbugs §2`)→ 必被要求做 anomaly 基线对比。→ roadmap 加:基线套件含 RCA/anomaly;排期 RESTifAI delta + AGORA+ rebuttal 进正文;ToV 章;可复现性打包。 |

## Reviewer Top-3 的裁决
1. **ResponseEnvelope 误述** → **ACCEPT**(integrity-critical;已 grep 坐实仍在 honest 版)。最高优先,入 T3 + A-main。
2. **venue gate + Frankenstein** → **PARTIAL**:venue 已 web 判定**开**(README 过时需更正);"Frankenstein"**REJECT**(跨 checkout 混淆);真动作=honest 版定为 canonical 覆盖主 checkout。
3. **prevalence 升级为可辩护 design** → **ACCEPT**(承重)。

## 由 disposition 触发的 roadmap 变更(已并入 ROADMAP-EXEC v2)
- T3 加:**修 ResponseEnvelope 叙述 + Fig1**(C3);**确认窗口=开**(C4,已定);**honest 版覆盖主 checkout**(C4 frankenstein 真动作)。
- T1 升级:**ground-truth 标注 + 基线 oracle 套件(status/schema/LLM-body,不得用自家 ResponseEnvelope)+ RCA/anomaly 基线 + 统计框架**(C1/C2/C6)。
- 新增**一致性管控**步(C4b)、**outage-driven 作为 framing 决策 + T2 失败预案**(C5)、**ToV/可复现性/RESTifAI-delta/AGORA+-rebuttal 进正文**(C6)。
- VERDICT §6 纠正:honest 版已修大半硬伤,余 ResponseEnvelope+Fig1。
