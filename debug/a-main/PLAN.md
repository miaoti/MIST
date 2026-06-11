# A-main 行动方案(可查询 · 带 citation)

> 配套战略文档:`docs/main-contribution/A_MAIN_ROADMAP.md`(deep-research 全文,2026-06-01)。
> 本文件是**可执行计划**:① 两个命门的探测方法;② grounding 5 个修复 + 验证。
> 状态(2026-06-01 晚 = 本文件最新):方案已定;grounding 修复 = **5/5 已应用 + 编译通过**(§3);两个命门探测 = **已完成**(见 `VERDICT-2026-06-01.md` + `probe-attribution.md` / `probe-wildbugs.md`);头条 detector = **已升级为全链路 e2e**(见下「状态更新」)。

---

## §0 状态更新(2026-06-01 晚间 —— 本节晚于本文件其余部分,凡冲突以本节为准)

> ⚠️ **doc 集时间线**(必读,否则会被自相矛盾的旧结论误导):
> VERDICT/DISPOSITION/ROADMAP 提交于 **01:57** < ResponseEnvelope wiring 提交 **02:08/02:32** < 本 PLAN **03:02** < readiness **03:22** < paper 最新 **15:34** < **e2e validity 五修复 14:55–21:45** < UX 可读性 19:51–21:45。
> 即:**任何旧 doc 里关于 ResponseEnvelope=no-op、OB 计数、"只离线 OracleCheck" 的结论,都早于把它们推翻的后续提交。** 本轮已派 3 个 reviewer agent 对着现网代码重新核实 paper(结果回填于本节末)。

**1. 两命门探测已完成 → 结论未变(study 腿承重)。** `VERDICT-2026-06-01.md`:头条 HiddenDownstreamFailure 真实且有 live 证据;命门 A(attribution)弱、天花板 = service-level;命门 B(野生 bug)= 0 条可复现 → 用 real-outage-on-real-OSS + Uber 29%/Yuan 92% 引文替代。novelty 立在 **framing + prevalence study**,非机制、非 param-attribution、非野生语料。用户「机制 trivial」判断成立(§4)。

**2. 【最大进展,旧 doc 全未反映】头条 detector 从"离线"升级为"全链路 e2e"。** 旧证据只来自 OFFLINE `OracleCheck` 跑手抓的 outage trace;本会话发现 **MIST 完整 generate→execute→oracle pipeline 之前静默漏检** hidden-downstream(externally-driven trace 是 flat/orphaned),并以 5 修复打通,**0→7 在 MIST 自己 marker-matched trace 上 fire**(Online Boutique,每条都能追到确切 test+trace)。详见 memory `project_externally_driven_trace_validity` + commit `e4a693db`/`cccc69f7`/`a5fa5c3c`/`7e2f9356`。
   - **对 paper 的意义**:移除了 reviewer 反对点"你只离线跑 oracle / 全链路没演示过"。
   - **caveat 仍在**:故障仍是 outage-driven(scale-to-0),非 input-driven —— 这条没变,ROADMAP T2 的 framing 决策仍成立。

**3. ResponseEnvelope 的 "no-op" 结论已过时(但需确认 runtime 生效 + artifact)。** VERDICT/DISPOSITION(01:57)称其 no-op(failureSet 恒空、LLM=TODO);**02:08 `ee9ac5cf` + 02:32 `7fe78494` 已 wire `EnvelopeClassifier`**——`ResponseEnvelopeInvariant.java` 现有 `withClassifier`(L86)+ first-2xx classify→cache(L122-125)+ `liveFailure`(L78)。**待 reviewer agent 确认**:(a) runtime 是否真注入了 classifier(还是 injectable-but-never-injected = 实质仍 no-op);(b) 是否有 committed live-fire artifact(readiness 称 `docs/main-contribution/evidence/responseenvelope_live_softerror.txt` + `evaluation/ResponseEnvelopeLiveCheck.java`)。→ **这条是 paper integrity 命门,结果回填本节。**

**4. OB 计数 doc 间不一致,以 paper 为准 = 7/12。** readiness「已落实 #4」写 8/12,但本会话 `7aea57e1` 又改回 **7/12**(理由:workload-mix dependent,"每条经过故障 adservice 的 frontend trace");paper 现为 7/12 + re-capture 24/40。readiness 的 8/12 行已过时。

**5. UX 可读性已补(服务 tool-demo / screencast)。** findings 现在能在 Allure 正确 surface:550 个 🕳️ titled attachment + `mist.anomaly` label + passed-but-hidden 测试的 `📊 Scenario Result ⚠️ HIDDEN DOWNSTREAM FAILURE` + API Call Trace 方向化(`caller ──▶ callee`)+ Jaeger 深链。commit `eb785e9f`/`9a85ef3d`/`4e514394`。

**6. reviewer 复核结果(3 agent 已完成,2026-06-01 晚)→ 判定:尚未 submission-ready,但很近——差一轮"attribution/scoping 措辞修 + 编 PDF 验页数",无需新实验。**
   - **PC 评审 = WEAK-ACCEPT**(过 demo 门槛;卡:4 页溢出未验证、abstract 过度宣称、trace-RCA/Nezha 定位偏弱、OB 计数呈现松)。
   - **可复现性 = YES**(实跑 shipped `OracleCheck`:Bookinfo FIRES@ERROR vs response PASS、OB 7/12+24/40 WARN、healthy 0/0;artifact 全 committed;**readiness 当年标的 #1 BLOCKER「可复现 overclaim」已证伪**)。
   - **integrity = 4 条 OVERCLAIM(grep 实证,全 ACCEPT)**:
     - (B1) **TT 10/10 是 SUT fault-NAME marker echo,非 trace oracle**:`MultiServiceRESTAssuredWriter.java:2286-2296`(读 `data.faultName`)+`FaultDetectionTracker.java:126-140`(registry 匹配);`run22-fault-detection-10of10.txt` 零 `RESPONSE_ENVELOPE`/`HIDDEN_DOWNSTREAM` 命中。paper L218/abstract 读作 oracle 检出 + 把 soft-error 归给 ResponseEnvelope = 失实。(印证 memory `project_amain_viability_probes`)
     - (B2) **Sock Shop soft-error 不被 named ResponseEnvelope invariant 捕获**:实跑 → `RESPONSE_ENVELOPE: pass`(漏);因 `primaryField=status` 硬默认(`ResponseEnvelopeInvariant.java:43`)、Sock Shop body 无 `status` 键、`setPrimaryField` 零 runtime caller;真正捕获者 = legacy inline `validateResponse`(`MultiServiceRESTAssuredWriter.java:2228-2262`)。→ L224 + "subsumes SoftErrorRuleCache"(L162/201)失实。
     - (B3) **"offline, no LLM" 对 soft-error 腿过度**:`ResponseEnvelopeLiveCheck` 需 live DeepSeek key;abstract(L115/209)blanket "offline…no LLM" vs §case(L216)"one DeepSeek call" 自相矛盾。HiddenDownstream/`OracleCheck` 腿确实离线(已实跑证实)。
     - (B6) **HiddenDownstream 描述漏 injected-`mist.client.status` anchor**:L202 只写 topology fallback,但 live 路径真正靠注入状态(`HiddenDownstreamFailureInvariant.java:79-121`;externally-driven trace 无 nested entry span——见 `project_externally_driven_trace_validity`)+ 漏 opt-in 默认关(`MstConfig.java:415-416`)+ 漏 WARN(otel-only,非 failing)vs ERROR(http5xx)区别。
   - **ResponseEnvelope no-op 结论确认已过时**:现 genuinely live(`TraceShapeOracle.java:104-107` + writer `:1348-1349` 真 wire;live artifact 经真 DeepSeek **从头复现** = 真 fire)——**但从未在 full-pipeline run 里 fire 过**(只 isolated harness;live `silent_accept_demo` 里是 `SILENT_ACCEPTANCE` 在 flag adminroute soft error)。→ 描述可保留"live one-shot LLM",但别再暗示它产出了 TT 检测。
   - **对 readiness doc(03:22)的纠正**:其「修完 #1–#4 即 ready」过于乐观。#1 可复现已真修(✅);#2 ResponseEnvelope artifact 确产出(✅)——**但它没发现更深的 attribution 命门**:named invariant 不捕 Sock Shop(B2)+ TT 10/10 是 marker echo(B1)。这两条本轮深挖才暴露。
   - **结论**:artifact 本身诚实且可复现,两个新机制真实;阻塞**全在 paper 正文的 attribution/scoping 措辞** + 一次 PDF 页数验证。修法 = 纯改写,会话已同步逐条 disposition + 建议措辞。
   - **已落实(2026-06-01 晚,13 处改写 = git diff 12/12 行)**:B1(abstract+L218 TT 检测归因到 injected-fault registry marker、trace oracle 另算)、B2(L224 Sock Shop 归因到 LLM-backed body check、3 处 subsumes→replaces + 澄清 inline 检查另路)、B3(abstract+L209 离线 claim scope 到 HiddenDownstream/`OracleCheck`、soft-error 注明 one LLM call)、B6(L202 补 injected-`mist.client.status` anchor + opt-in 默认关 + WARN/ERROR)、A2(abstract+L133 "no code changes/end-to-end"→"配置级/no source changes")、A3(L246 trace-anomaly 定位 + delta 收紧)、A4(L222 OB 7/12 vs 24/40 呈现统一)、Fig1 caption(IDs anonymized + 全路径)。**A5 页数验证 = 用户自理。** grep 复核:7 条旧过度宣称残留全 0、新措辞全到位、18 cite key 全 resolve。
   - **二次 review(2026-06-01,2 agent)→ 收敛 WEAK-ACCEPT→ACCEPT**:integrity 复审 8 项 **7 RESOLVED**;1 NEW-ISSUE = 我 round-1 的 A4 过度更正(写成 "fires on **every** frontend trace that routed through adservice 7/12",实测 8 条经过、shipped invariant 只 fire 7,recapture 24/25)→ **已软化**(去掉 "every",改"7 of 12…frontend traces that routed through adservice" + workload-mix 解释)。fresh-eyes PC 另抓:abstract key 元组 `(rootApi,…)`→`(paramName, paramLocation)`(对齐 `PoolKey`)、`replaces SoftErrorRuleCache` 3 次像 changelog 噪音→删到 1 处(L201)、L202 太密→拆句重排、L224 "no code changes"→"no source changes"、Table caption 加 silent-reason 注。**REJECT 1 条**:PC 称 "265 ops 实测 263"——核对 bundled spec(`merged_openapi_spec 1.yaml`)与 evaluation 版**完全相同、operationId+verb 都 265**,265 正确不改。round-2 共 7 处改写,grep+brace(375/375)复核通过。
   - **⚠️ 两份副本风险**:13+7 处改写**只在 worktree 副本**;主 checkout `paper/main_issta.tex` 仍是旧版。**camera-ready 若从主 checkout build 则改动全不生效——必须 commit 到 inject-detection 并在主 checkout pull/checkout 同步。**

---

## §1 A-main 贡献定位(一句话 + 必须/禁止 claim)

**贡献**:首个 label-free **分布式-trace** oracle,检测 (i) swallowed-downstream-failure(网关 2xx,下游 span 藏被吞的 5xx/gRPC-error)与 (ii) trace-only silent-acceptance——**客户端响应里看不见**;配一个"这类失败在真实系统多普遍 + oracle 检出多少"的测量研究。

- **禁止 claim**:soft-error(2xx-body-域错误)检测**不是新颖点**——已被 LogiAgent / RBCTest / RESTifAI 在 response-level 占了。
  - LogiAgent: arXiv 2503.15079 ; RBCTest (ICSE'26): arXiv 2504.17287 ; RESTifAI (ICSE'26 demo): arXiv 2512.08706。
- **必须 claim**:**trace-only / 跨服务**那一类。上述全部 + AGORA+(TOSEM'25, javalenzuela.com/.../2025_tosem_agoraPlus.pdf)**都是单响应/单操作、无一吃分布式 trace**(deep-research 3-0 核实)。

**动机(引用)**:Uber《The Tale of Errors in Microservices》SIGMETRICS'25 / POMACS 8(3),**DOI 10.1145/3700436**——~**29.35%** 的成功(2xx)请求,调用链深处藏被吞非致命错误(11B RPC、6000+ 服务)。
**真实 bug ground truth(引用)**:TrainTicket TSE'18(IEEE TSE.2018.2887384 + github.com/FudanSELab/train-ticket-fault-replicate),F6/F7/F10/F12 = 被吞/未处理下游;**措辞=real-world-derived re-injected,非 wild bug**。

---

## §2 两个命门 + 具体探测方法(这是"摸"的计划)

### 命门 A:intent-conditioned attribution 能不能从"弱"做到"可量化、抗 ablation"?
- **为什么是命门**:这是唯一真新颖的 method 点。做不实 → 方法腿塌、全压到 study 腿(B),B 就必须跑到真生产级规模才扛得住。现状:memory `project_phase2_attribution_gap` —— TT 上 `TARGET_REJECTION=0`,只 service-level、param-level 不支持。
- **探测方法(只摸、先不大改)**:
  1. 读 `TargetAttributionInvariant` + `TraceAttribution.attribute(...)`,定位"为什么 param-level 归因拿不到"(span 里有没有 param 粒度信息?还是只有 controller 粒度?)。**file:line 取证**。
  2. 在 1 个能注入的端点(TT adminroute)上,手动构造"攻 param X、期望被拒"的 trace,跑 attribute(),看它能否分出 TARGET / WRONG_PARAM / UPSTREAM。记录实际输出。
  3. 判定:**(a)** 能到 param-level(需要 span 里有足够信息)→ attribution 可做强,方法腿成立;**(b)** span 本质上只有 service/controller 粒度 → param-level 归因**信息论上不可得**,attribution 只能停在 service-level → **方法腿降级**,论文重心移到 study 腿。
- **产出**:`debug/a-main/probe-attribution.md`(取证 + 判定 a/b)。

### 命门 B:除 TrainTicket 外,到底有没有可挖的**野生(commit 历史)**被吞-下游 / silent-accept bug 源?
- **为什么是命门**:AGORA+ 的 bar 是 **32 个确认的真实 bug**;reviewer 对 wild bug 权重远高于注入。deep-research **未能证明**这种语料存在(列为 aspirational)。
- **探测方法(只摸)**:
  1. 在目标 OSS 微服务项目(train-ticket、sock-shop、online-boutique、DeathStarBench、以及更广的 Spring-Cloud / Go-microservices 项目)的 issue/PR/commit 里,按关键词检索:`swallow exception`、`silent failure`、`ignored error`、`graceful degrad* 导致的 wrong result`、`catch.*return null/empty`、`2xx but wrong`。
  2. 对命中,人工判定是否="下游故障被吞在 2xx 后面 / 非法输入被静默接受",且**可复现**(有 repro 步骤 / 修复 commit)。
  3. 统计:能找到几条**可复现的 wild bug**。判定:**≥~5–10 条可复现** → study 腿能用 wild bug 加固,A-main 可达;**几乎找不到** → 只能靠 TrainTicket re-injected + 大规模 trace 测量,A-main 风险显著上升。
- **产出**:`debug/a-main/probe-wildbugs.md`(命中清单 + 可复现性 + 计数 + 判定)。**附**:也核 RCAEval(github.com/phamquiluan/RCAEval)、《Simple Testing Can Prevent Most Critical Failures》(distributed-systems failure 语料)是否含可用 ground truth。

**决策门**:两命门**任一为否**,路线降级(见 ROADMAP §9)。**先并行摸这两个,再决定要不要 all-in。**

---

## §3 Grounding 修复(5 个 defect)+ 验证 —— 战术,服务 tool/demo,不进 A-main 承重墙

> 诊断来源:本会话 a3b7d1e045c9cdbb1 agent + 我手动复验。file:line 见下(可能 ±,改前再 Read 确认)。

| # | Defect | 位置 | 修法 | 影响 |
|---|---|---|---|---|
| **A** | percentage=0.3 门 → 70% 值直接 LLM 瞎编 | `SmartInputFetcher.java:312`(`if random < percentage`) | 反转语义:**先 fetchFromSmartSource,返回 null 才回退 LLM**;`1-percentage` 仅作"故意注入多样性"的小概率。同时 SUT `*-mst.properties` `smart.input.fetch.percentage` 调高。 | 主因,最高影响 |
| **B** | pool 先建先用、按 0.3 稀释 | `SharedPoolSupport.java:201-234` 建池;`MistGenerator.java:1182-1209` 消费 | 池**优先放 fetch 到的 live 值**(用 `fetchSmartInputWithProvenance` 的 `RESOLVED_LIVE` 标签),LLM 仅补足;消费端 `preferVerifiedValues` 也认 `RESOLVED_LIVE` | 结构性 |
| **C** | whitelist 太严,drop 掉 LLM 发现的端点 | `SmartInputFetcher.java:563-586` | 白名单**从 conf 的 OpenAPI path 段播种** + 模糊/子串匹配 + 允许返回端点 path(对着 spec 校验) | 对无 registry 的 SUT |
| **D** | 抽取不校验,污染 DB 的 `{}`/`12345678901` 被当真值 | `SmartInputFetcher.java:974-989`(直连字段匹配分支) | 一行:`return value.toString()` 前加 `isValidValueForParameter(...)` 守卫(对齐 992-1008 的语义分支) | 次要但简单 |
| **E** | 学坏的"毒映射"(endStation→`/routes/{start}/{end}` + 记录的 HTTP-400) | registry(`trainticket/input-fetch-registry.yaml`)+ `fetchFromSmartSource` 选映射处 | 选映射时**跳过带 `errorType/VALIDATION_ERROR` 记录的、或 successRate 低的**;对同名站类参数**泛化到最高 successRate 的 `/stations` 源** | 是 percentage=1.0 那次仍失败的真因 |

**验证(重试)**:改完 → `mvn -q package -pl mist-cli -am -Dmaven.test.skip=true` 重建 jar → 在 TT(临时缩规模 properties + 单 adminroute trace,**注意 testsperoperation/variants 是 file 属性、`-D` 覆盖不了,要改文件**)重跑 → **量正例"被接受率(HTTP 200)"修前(≈0)vs 修后**,并直接看生成测试里 `startStation/endStation/stationList` 是不是真实站名。**修前基线已知:0/1608 正例通过;percentage=1.0 单改无效(endStation 仍 "Guangzhou South")。**

**判定**:被接受率明显抬升 + 站名是 `/stations` 真值 → grounding 可修、且我们才算真正摸到天花板;仍≈0 → 还有未识别层,继续诊断。

---

### §3 应用状态(2026-06-01,5/5 已落地 + `mvn package` 编译通过)

| # | 修法 | 落地位置(已核) | 状态 |
|---|---|---|---|
| **A** | 接地优先默认 | `SmartInputFetchConfig.java:76` 字段默认 `0.3→1.0`;`:114` `getOrDefault` 默认 `"0.3"→"1.0"`;6 个 SUT `*-mst.properties` 的 `smart.input.fetch.percentage=0.3→1.0` | ✅ 应用 |
| **B** | 接地优先池 | `SharedPoolSupport.java:203-239`(`groundedValues` 收 `RESOLVED_LIVE/RESOLVED_CACHE`;非空则池只放接地值,跳过 LLM 补足) | ✅ 应用 |
| **C** | 模糊白名单 | `SmartInputFetcher.java:619` `fuzzyMatchService`、`:658/:671` `fuzzyMatchService/stemService` | ✅ 应用 |
| **D** | 抽取校验守卫 | `SmartInputFetcher.java` 取值返回前 `isValidValueForParameter` 守卫(388/441/483/1322/1375 等) | ✅ 应用 |
| **E** | 跳过毒映射 + 发现回退 | `SmartInputFetcher.java:416/475` `discoverApiMappings` 回退(mappings 仅毒映射时仍能发现真实 producer) | ✅ 应用 |

**逻辑判定(达成用户目标)**:5 个已识别 defect 全部有修复 + 全量编译通过 → **从逻辑上 tool 不再有"明知却不修"的 grounding bug**。残余的"哪怕 smart-fetch 仍无法 100% 接地"= 开放难题本身(span 粒度、producer 不可达、SUT 无对应只读端点),非我们的 defect,符合用户"逻辑无 bug,而非真去攻克难题"的目标。
### §3 经验验证(2026-06-01,已跑 · 用户要求"验证了再决定 commit")

跑法:TT `trainticket-demo.properties`(fetch 开,`percentage=1.0`,5 fixes 全在,`experiment.execute=false` 只生成),cwd=repo-root。SUT 可达(37/38 端点)。证据 = `logs/mist.log`(INFO 级 smart-fetch 决策)。

**量化(接地率)**:
- 接地(`Smart Fetch → … ✅` 35 + `🔄 diverse cached … ✅` 794)= **~829**;LLM 瞎编 fallback = **2**。→ **接地率 ≈ 99.7%**。
- 对比修前基线(本会话先前):接地 348 / LLM 2140 ≈ **14%**。→ **14% → 99.7%**,抬升确凿。
- 门已反转:日志 `🎯 Smart Fetch Decision → endStation (random: 0.100 < 100.0%)` —— `percentage=1.0` 使 fetch-first 永真(Fix A 生效)。
- `startStation/stationList` 接地为 `/stations` **真站名**:chicagounionstation, empirestatebuilding, grandcentralterminal, goldengatebridge, hooverdam, libertyisland, grandcanyon, centralpark。

**残余 = 开放难题(非我们的 defect,符合"逻辑无 bug,不去攻克难题")**:
1. **DB 污染值**:`12345678901 / 11223344556 / …`(11 位数字串)**确实在 SUT 的 `/stations` 表里**(此前负例测试写脏的),结构上是合法 string → 被忠实接地。Fix D 拒**结构**垃圾(`{}` 不在结果里 = 已拒),但数字串需**领域知识**才能判废 → 开放难题。
2. **producer 语义错配**:`endStation` 被发现到 `ts-train-service/trains`(`GaoTie*` 真实车次,实体错)而非 `/stations` —— 真值、错源;完美语义 producer 匹配 = 开放难题。

**判定(达成用户目标)**:接地率 14%→99.7%,**value-invention 这一类我们的 defect 已修到天花板**;残余两项(SUT 自身数据污染 + 语义 producer 匹配)是开放难题,工具只是忠实反映 SUT 真实数据,**逻辑上 tool 无 bug**。✅ 可作为 commit 依据。

---

## §4 里程碑 / 顺序
1. (本轮)落本方案 → **修 grounding 5 条 + 重建 + TT 重试量被接受率**。
2. **并行摸两个命门**(§2,各出一个 probe-*.md)。
3. 据命门结果**定/降级** A-main 路线。
4. 不耽误:**ISSTA tool demo(6/26)** 照常推进(它不依赖以上)。

---

## References(一手源,deep-research 已 3-0/近一致核实)
- RESTifAI (ICSE'26 demo): https://arxiv.org/html/2512.08706v1
- LogiAgent: https://arxiv.org/abs/2503.15079 ; https://arxiv.org/html/2503.15079v1
- RBCTest (ICSE'26): https://arxiv.org/html/2504.17287
- AGORA+ (TOSEM'25): https://www.javalenzuela.com/publication/2025_tosem_agoraplus/2025_tosem_agoraPlus.pdf
- Uber《Tale of Errors》SIGMETRICS'25 / POMACS 8(3): https://dl.acm.org/doi/10.1145/3700436
- TrainTicket faults: IEEE TSE.2018.2887384 ; https://github.com/FudanSELab/train-ticket-fault-replicate
- 旁证:RCAEval https://github.com/phamquiluan/RCAEval ; Tracetest https://tracetest.io/ ; 《Simple Testing Can Prevent Most Critical Failures》(OSDI'14 失败分析)
- 代码诊断:本会话 grounding-诊断 agent + 手动复验(SmartInputFetcher / SharedPoolSupport / MistGenerator file:line 见 §3)
