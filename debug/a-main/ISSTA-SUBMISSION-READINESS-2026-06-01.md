# ISSTA tool-demo 提交就绪评审(2026-06-01)

> 方法:3 个并行 agent 对着已提交 artifact 核实(reproducibility / empirical-backing / consistency-presentation)+ 综合。假设 screencast+Zenodo 已就绪。
> **判定:接近,但还不是 submission-ready。** 内容/呈现强;2 个实质问题 + 1 正确性 bug + 1 数字不一致需先修。

## 必修(提交前)

| # | 严重度 | 问题 | 证据 | 修法 |
|---|---|---|---|---|
| 1 | **BLOCKER(tool-demo 命门)** | 可复现性 overclaim:headline demo 跑不出来 | `trainticket-demo.properties:101` base.url=129.62.148.112(实验室单机,非"public deployment");`experiment.execute=true`/`jaeger.enabled=true`/`smart.input.fetch=true` 全依赖活 SUT;in-JVM 编译需 JDK 非 JRE(`MistRunner.java:2139`);**作者自证** `bookinfo_e2e_pipeline.md:94-101` 用了外部 javac;README 离线 cache `-Drandom.seed=42` 假(`.mist/` 被 gitignore);run `trainticket_twostage_test_42` 说 bundled 但 repo 无此名文件(实为 `run22-fault-detection-10of10.txt`) | ① 让默认 demo 真离线自洽:un-gitignore + 提交 `.mist/llm-call-cache.json`,并把 recipe 指向 `trainticket-demo-noexec.properties`(已存在,execute=false)或 trace-replay;② 别叫"public deployment",在 §case 写明真实前提(JDK、SUT 起着);③ abstract 软化"runs end-to-end via java -jar … no code changes"——把 Limitations(line 248)已有的诚实 caveat 提上来;④ 提交那个 run 或删"bundled" |
| 2 | **MAJOR(integrity)** | ResponseEnvelope soft-error 检出无 live artifact | 代码(`ResponseEnvelopeInvariant.java`)+ 10 单测有;但**无任何已提交"它触发"的 run**;headline 10/10(run22)是 **marker 驱动、比分类器(ee9ac5cf/7fe78494, 6-1)早 4 天**(run22 提交 5-28,零 ResponseEnvelope 字样);`debug/negative_test/README.md` 证实是 fault-name marker 匹配 | 二选一:(a) 产一份**已提交的 live-fire artifact**——真 DeepSeek 跑真实 soft-error body(Sock Shop `status_code:500` / TT `status:0`),记录 RESPONSE_ENVELOPE=FAIL;或 (b) 软化措辞:soft-error 检出描述为"已实现能力(代码+单测)"而非已演示的经验结果,10/10 明确为 marker-confirmed(line 218 已 hedge;abstract/138/201 需不暗示已端到端演示) |
| 3 | **MAJOR(正确性)** | gap-label bug:line 169 Root API Mode 关"(G2)",应为 (G3) | line 123 定义 G3=fabricate state;line 128 正确写"Closes G3";line 169 复述 G3 定义却标 G2 | line 169 `(G2)`→`(G3)`,一字 |
| 4 | **MAJOR(数字)** | OB "7/7 outage traces" 与 artifact 不符 | `boutique_adservice_outage.json` = 12 trace、**8** 触发(otel ERROR on frontend→adservice),非 7/7;WARN/0-healthy/38 都对 | 按已提交文件重述(如"8/12 fire / 0 healthy"或澄清 7/7 计的是什么) |

## 次要(打磨)
- line 186 "eliminating" → "reducing"(无测量的绝对 claim)。
- abstract "generated variants" vs §5 "test cases" 统一。
- "15–20 multi-root sequences"无 artifact(实跑 370/22)→ 软化/限定。
- 架构图 "6 invariants" → "4 learned + 2 structural"(可选,消歧)。
- "37 services" 推导可加一句(39 个 `/api/v1/*` 前缀 − actuator/error)。
- **长度**:可能略超 4 页(2 全宽图+表+算法+listing);超则降 fig:trace 为单栏。

## clean(已核实无问题)
265 ops 正确;所有 \ref/\cite resolve;架构图与正文一致;baseline 表已删;Limitations 诚实(承认 outage-driven、2-SUT、外部 JDK);SUT 数=4 处处一致;Table 2 逐行对得上证据;Sock Shop body 逐字对。

## camera-ready 才需(非 blocker)
ISBN/DOI 占位(52-53)、bib 的 TODO DOI、screencast/Zenodo。

---
**结论**:修完 #1–#4(其中 #3/#4 各几分钟,#2 软化版几分钟、artifact 版需一次 LLM 跑,#1 需小 repo 改 + 文字 reframe)即 submission-ready。#1 是 tool-demo 最关键的一条。

---

## 已落实(2026-06-01,A+B 全做)

- **#1 可复现性 → 修复**:发现 `trainticket-demo-noexec.properties` **本就完全离线**(`llm.enabled=false`/`hardcode`/`jaeger=false`/`smart-fetch=false`),smoke 验证离线跑到 Writing 124 items、无 SUT/LLM 网络。**因此 LLM cache 无需提交**(离线靠 hardcode)。abstract + §case 重构:离线路径(noexec 生成 + `OracleCheck` 复现 G2 + `ResponseEnvelopeLiveCheck` 复现 soft-error)为头条,live-SUT 执行降为可选并写明前提(SUT 部署 + JDK);删"public deployment";Data Availability 列入新 harness/transcript。
- **#2 ResponseEnvelope live artifact → 产出**:`evaluation/ResponseEnvelopeLiveCheck.java`(harness)+ `docs/main-contribution/evidence/responseenvelope_live_softerror.txt`(真 DeepSeek 跑 TT soft-error body,`RESPONSE_ENVELOPE: FAIL ... status=0 classified as failure (LLM, cached)`)。§case 挂上引用。
- **#3 G2→G3**:line 169 已改。
- **#4 OB 7/7 → 8/12**:已按 committed trace 重述(8 of 12 outage、0 of 12 healthy)。
- **次要**:eliminating→reducing、variants/test-cases 统一、15–20 软化、37 加推导、架构图标签 4+2,全已改。
- **离线可复现已验证**:`OracleCheck` 对 committed Bookinfo masked /reviews FIRES(ERROR)、OB outage FIRES(WARN)、response-level 全 PASS——零 SUT/LLM。

**剩余非 blocker**:screencast/Zenodo(作者)、ISBN/DOI(camera-ready)、build PDF 目视图与页数(无 pdflatex 在此);`main.tex`(ICSE)仍待全量同步。
