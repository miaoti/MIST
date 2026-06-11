# A-main 执行 Roadmap v2(2026-06-01,四轨并进,已过 reviewer+disposition)

> v1→v2 依据:`DISPOSITION-2026-06-01.md`(reviewer a99312bd 的逐条裁决)。承重判断见 `VERDICT-2026-06-01.md`。
> **硬事实**:ISSTA tool-demo 截止 **2026-06-26 AoE(web 核实=开)**,notif 7/24,camera 8/7。repo `README_ISSTA.md` 说"已关"是**过时的,需更正**。
> **两份论文**:worktree `paper/main_issta.tex`(33d872b4)= honest 版(4 SUT、无 baseline 表、诚实 outage caveat);主 checkout 同名文件 = 旧版(Core Innovations、10/10-vs-baseline 表、one SUT)。**honest 版定为 canonical,需覆盖主 checkout。**

---

## 关键路径与并行结构
```
现在 ──┬─ [Phase 0 ≤3d 决策+integrity] ──┬─ T3 ISSTA 冲刺(6/26 时间盒) ──► 投稿
       │                                  └─ T1 prevalence DESIGN+infra(长前置) ──► [Phase 2 生产级执行=承重] ──► A-main
       └─                                                                          ├─ T2 input-masking(framing 决策级)
                                                                                    └─ T4 attribution value-match(可选)
```
承重 = T1。T3 并行 deadline 冲刺、严格时间盒不蚕食 T1。T2 比 v1 认定的更接近 framing 承重(见下)。T4 可选。

## Phase 0(现在,≤3 天)— 决策 + integrity 急修
- **[C3 最高优先] ResponseEnvelope integrity**:论文(honest 版 `:138/:193/:201/:218`)称 ResponseEnvelope 经"one-shot LLM"是 soft-error 决定信号,但代码里它是 no-op(`ResponseEnvelopeInvariant.java:130` failureSet 恒空、`:72` FAIL 路径死、`:77` LLM=TODO);真实检出是 SUT marker(`MultiServiceRESTAssuredWriter.java:2181`)。**二选一:要么实现它(填 failureSet/接 LLM),要么删 claim 并把 soft-error 归因改诚实。两篇论文同步改。**
- **T3.0**:① 确认窗口=开(已定);② 把 honest worktree 版**同步覆盖主 checkout 旧版**(消除跨-checkout 矛盾);③ 列 T3 余下真硬伤=ResponseEnvelope 叙述 + Fig1。
- **T1.0**:锁 SUT 名单(按 trace-context 传播筛;Sock Shop 仅 G1)。已有 Bookinfo+OB,候选 DeathStarBench/TeaStore。**定 prevalence 的 ground-truth 标注法 + 基线 oracle 套件**(见 Phase 2 T1)。
- **决策门**:据工作量定 ISSTA 6/26 投/不投。

## Phase 1(并行)— ISSTA 冲刺 ∥ A-main DESIGN+infra
- **T3(deadline 盒)**:① 修 ResponseEnvelope 叙述(Phase 0 已决方向);② Fig1 换成**已提交的 OB/Bookinfo 真 G2 trace**(替掉虚构全-200 TT 图 `:193`,其 trace id `d4c577d4` 无 artifact);③ 录 screencast + Zenodo;④ 主 checkout 覆盖为 honest 版。**注:baseline 表、计数、4-SUT、outage caveat 在 honest 版已修(C4 already-fixed),不重做。**
- **T1 DESIGN(承重前置,不止部署)**:把 prevalence 从"一个比率"升级为可辩护设计——
  - **ground-truth**:定"这条 2xx 真藏了**缺陷**" vs "这是设计内优雅降级(如 OB adservice 的 otel=ERROR)"的独立标注法。否则 detector(`HiddenDownstreamFailureInvariant.java:80-82` 把任何后代 5xx/ERROR 当 hidden)只是在数"swallowed-error span",不是 hidden *failure*。**[C2 中心 confounder]**
  - **基线 oracle 套件**:status-class + JSON-schema + **body-reading LLM(LogiAgent)**;**禁用 MIST 自家 ResponseEnvelope 当 body 基线**(它 no-op,会把漏检率虚抬到 100% 同义反复)**[C1]**;**加 RCA/trace-anomaly 基线(TraceRCA/Nezha/RCAEval)**——顶会必问"anomaly detector 漏什么你抓什么"**[C6]**。
  - **统计框架**:采样框架(端点/workload mix)、CI、SUT 间方差、per-request vs per-endpoint 分开报。
  - infra:部署 +1–2 SUT;workload generator;fault model。

## Phase 2(6/26 后)— A-main 主线
- **T1 生产级执行(承重)**:跨全 SUT 出头条数字(普遍性 + 各基线相对 trace oracle 的漏检率),带 ground-truth、基线套件、统计。
- **T2 input-masking(framing 决策级,非小 spike)[C5]**:全部现有 G2 证据是 **outage-driven**(`bookinfo_e2e_pipeline.md:111`、`boutique_e2e_pipeline.md:52`)——这改变"论文在测什么"(SUT 韧性 vs 可测缺陷)。**预先承诺失败预案**:若 N 天 elicit 不出 input-driven masked failure,则正文显式 reframe 为"trace oracle 检测 + 生成是载体",**abstract 不得暗示 input-driven 触发**。
- **T4 attribution value-match(可选)**:读 `Span.logs` 做值匹配,仅对响亮类有效;保留 service-level 默认;低 ROI,仅当不招"仍弱"批评时纳入。

## 必进正文(C6,A-main)
ToV 章(ground-truth 构造/outage-driven 威胁);**RESTifAI delta**(SilentAcceptance 是其 prior-art,需明确增量);**AGORA+ 不可迁移 rebuttal**(`probe-wildbugs §3.4`:trace-only 类无法像 AGORA+ 打公开 API);可复现性打包(JRE/javac、3s ingest、flag 默认值等脆性)。

## 一致性管控(C4b)
共享 claim 设**单一真相源**(机制描述);改一处镜像/明确 scope 另一处。当前活跃暴露:ResponseEnvelope(C3 修后消)、SUT 数/成熟度、attribution framing。

## 风险与缓解
| 风险 | 缓解 |
|---|---|
| ResponseEnvelope 误述被 desk-reject/credibility 打击 | Phase 0 最高优先修(实现或删 claim) |
| prevalence 沦为同义反复(status 必然 0、body 基线是自家 no-op) | ground-truth + 外部基线套件 + RCA 基线 |
| outage-driven 改变论文性质 | T2 预案 reframe;abstract 不超售 |
| input-masking elicit 不出 | 时间盒 + 预承诺 reframe |
| "生产级"算力/SUT 运维 + 可复现性 | 先小规模打通再放量;打包 artifact |
| 机制平凡 | study(普遍性+漏检率+RCA 对比)+ framing 扛新颖 |

## 第一步(可立即执行)
1. **[C3] 决策 ResponseEnvelope**:实现 vs 删 claim —— 这步定两篇论文的诚实性,优先级最高。
2. **T3.0**:honest 版覆盖主 checkout + 列 Fig1/ResponseEnvelope 余项。
3. **T1.0**:核 DeathStarBench/TeaStore trace-context;起草 ground-truth 标注法 + 基线套件清单。
