# MIST — 主贡献再定位文档

> 给作者自己看的"做了太久快忘了"导航。English 术语/claim/数字逐字保留(与论文一致),解释用中文。
> 反映状态:2026-06-03,branch `inject-detection`,commit `45486e5e`(论文 `paper/main_issta.tex`)。

---

## 0. 一句话记住它(The ONE thing)

**MIST 的头号贡献 = `HiddenDownstreamFailure` invariant:一个 label-free、LLM-free 的 trace-shape oracle,能抓到"client-facing entry 返回干净的 2xx、但 call tree 深处某个 span server-errored 并被吞掉(swallowed)"这一类失败——status / schema / body / LLM-body oracle 全都看不见,只有它 fire。** 跨 HTTP 和 gRPC、4 个真实微服务 SUT、且**离线可复现**(无需 SUT、无需 LLM,审稿人 10 分钟可验)。

其余都是支撑:工具叫 MIST,把 **distributed trace 同时当 generation 输入 + 一等 functional oracle**,在黑盒 REST test generator 里,不 instrument SUT。

---

## 1. 问题:为什么这是个真 gap(reviewer 想看的 "negative space")

黑盒 REST tester(EvoMaster / RESTler / AutoRestTest …)从 OpenAPI 采样输入、**停在 HTTP status class**。微服务里有三个它们看不见的 gap:

- **G1 — soft error**:`HTTP 200 OK` 但 body 里 `status:0, data:null`(domain-level rejection)。证据只在 body。
- **G2 — hidden downstream failure(头条针对的)**:client-facing entry 返回 `HTTP 200`,但 call tree **深处**某个 service `5xx`-errored,被 caller 的 error handling **吞掉**。唯一证据是一个**不在 response path 上**的 span → status / schema / 连 body-level(body 看着干净)全 miss。
- **G3 — fabricated state**:要驱动内部端点就得伪造只有上游调用能产生的 ID(orderId、session token);一次 fabrication 失败和一个真 defect 不可分。

---

## 2. 头号贡献:`HiddenDownstreamFailure`(扎实的那条)

- **是什么**:trace-shape oracle 里的一个 invariant。**label-free**(无人工写的断言、无 learned baseline)、**LLM-free**(oracle 时不需要任何模型)。代码 `mist-core/.../oracle/shape/invariant/HiddenDownstreamFailureInvariant.java`,类型 `ShapeInvariant<Void>`(T=Void = 字面上无 learned data)。
- **触发规则**:client-facing entry 是 2xx,但**更深的** span 有 `http.status_code >= 500` **或** `otel.status_code = ERROR` → fire。深处的 `4xx` 是 benign(不 fire)。
- **两档 severity = 操作性 triage**:
  - HTTP-5xx swallow → **`error`**(fail-stop 回归门禁,测试红)
  - otel-only swallow(如可容忍的 gRPC 降级)→ **`warn`**(非阻塞 review 信号)
- **live vs offline 两条路径**:外部 client 不被 trace、entry 自己的 inbound span 常缺失 → **live** 路径用测试注入的 client status 当 entry 锚点、仍然扫 downstream span 找被吞的错;**offline replay** 用 trace topology 找 entry。两路都查 downstream span。
- **为什么新(differentiator)**:把 trace 同时当 **generation 输入 + 一等 oracle**,且这条 invariant **label-free/LLM-free**,在黑盒 REST generator 里、不 instrument SUT。据我们所知没有别的 open-source REST generator 这样做。

---

## 3. 证据 —— 扎实的 vs 诚实标注弱的

### ✅ 扎实(6/7 轮评审 + 真跑 artifact 都认可)
- **Bookinfo(HTTP,ERROR)**:`GET /api/v1/products/{id}/reviews` 在 ratings outage 下返回 `HTTP 200`,深处 `reviews→ratings` `503 / otel=ERROR` 被吞 → `HiddenDownstreamFailure` **fire ERROR、测试红**;response-level oracle pass(miss)。= Figure 2 + Table 1。
- **Online Boutique(gRPC,WARN)**:`frontend` 返回 `HTTP 200`,被吞的 `frontend→adservice` gRPC 调用带 `grpc.status_code=14`、`otel.status_code=ERROR`、`http.status_code` 是误导性 `200` → fire **WARN**(otel-only,无 HTTP 5xx)。
- **离线可复现**:`OracleCheck` 在 committed trace(`docs/main-contribution/evidence/bookinfo_e2e_traces/masked_reviews_ratings_outage.json`)上重跑,**无需 SUT、无需 LLM**。审稿人真跑过、逐数字对上。
- **定量(key-independent 表述)**:每条经过失败 `adservice` 的 frontend-rooted trace 都 fire(7/12 outage、0/12 healthy;更大 recapture 24/40、0/30)。0 false-positive on healthy。

### ◐ 诚实标注的弱项(多轮 + 真跑实证后定下来的)
- **TrainTicket 15,036 / 10-of-10 ≠ oracle 证据**:那 10/10 是 **SUT 自带 injected-fault registry 的 marker 匹配**(self-instrumented SUT 回显 fault name、MIST 匹配),trace oracle 独立计分、**没驱动它**。论文已明写 "a generation and scale demonstration, **not oracle evidence**"。
- **G2 是 outage 驱动、非 input-elicited**:两个案例都是手动把依赖 scale 到 0(`ratings`/`adservice`→0),不是 MIST 生成的输入诱发。论文已写 "input elicitation is left to future work"。**这是 generation↔oracle loop 在头条案例上没真闭合**——诚实承认。
- **`ResponseEnvelope`(G1)fallible**:真跑 DeepSeek 确认它**既 false-positive(`status:1, data:null`)又 false-negative(漏 Sock Shop 的 `status_code:500`)**——是 opt-in、低保证的 **LLM 腿**,不是头条。transcript 在 `docs/.../evidence/responseenvelope_live_3case.txt`。
- **`TargetAttribution` = 0%**:param 级归因在 TrainTicket 的 generic Spring controller span(`RouteController.createAndModifyRoute`)上**结构性失效**(token 不与注入参数名 `stationList` 重叠 → 全 `WRONG_PARAM_REJECTION`)。所以 Sniper 的 attribution **不靠它**,靠 by-construction。

---

## 4. 论文的三个 named contributions + 诚实状态

| 贡献 | 状态 | 关键点 |
|---|---|---|
| **Root API Mode**(G3)| 设计特性 | 只驱动 externally-reachable entry、从 trace 读内部,避免 fabrication;**未单独实测** |
| **Sniper Strategy** | 设计保证 **+ 实测覆盖** | (i) attribution **by construction**:one fault per variant,记录 `(r,k,c,v)`,每次 failure 确定性归因到一个 mutation;(ii) coverage **实测**:round-robin 确定性 enroll **7/9 fault categories**(`Regex_Mismatch`/`Enum_Violation` 无适用参数),覆盖适用 fault space 而非采样——**可 grep `run22` 报告验证** |
| **Trace Shape Oracle** | 一个 invariant family | **`HiddenDownstreamFailure` 是被 demo 的 headline**;`ResponseEnvelope` opt-in fallible LLM;`SpanTreeShape`/`StatusPropagation`/`TimingEnvelope` 需 per-SUT learned baseline(demo 里未 fire);`TargetAttribution` advisory(0% on TT)|

> **framing 决策(你已拍板)**:保持 3 贡献、诚实 scope——intro 天然区分"被 demo 的 headline(HiddenDownstreamFailure)"和"设计特性"。**不**重构成单一贡献、**不**额外加自承认 hedge。

---

## 5. 跟现有工作的区别(positioning)

| 别人 | MIST 凭什么不同 |
|---|---|
| AutoRestTest(MARL + GloVe SPDG)| 不 ingest trace;oracle 仍是 status/coverage |
| LogiAgent / RESTifAI / SATORI | LLM/spec 读 **response body / spec**,看不见 off-response-path span |
| Tracetest | assert over span 但要**人工写断言**、不生成测试、不 mine trace |
| TraceRCA / Nezha | 事后 RCA(post-hoc, learned 的事故解释),不是 pre-hoc functional oracle |

一句话 differentiator(§ Related Work):**"couple trace-driven generation with a trace-based functional oracle inside a black-box REST API test generator, using the trace as both a generation input and a first-class oracle without instrumenting the SUT."** —— 据我们所知没有别的 open-source REST generator 这样做。

---

## 6. 诚实的边界 / Threats(论文已写)

- **verdict 是 policy choice**:swallowed error vs **故意的 graceful degradation**(Fig 2 的 `reviews` 本身就是优雅降级)→ 两档 severity + opt-in 让团队 triage,不是无条件 gate。
- **firing rule 的 FP/FN 面**:retry 后恢复但留下 `ERROR` span → 会 fire(false positive);完全无 errored span 的 masking → 不 fire(false negative)。
- **范围**:G2 现于 **2/4 SUT**、outage 驱动。

---

## 7. 评审现状(7 轮独立冷读)

- R3/R4 = **3× Weak Accept**;**R5 / R6 / R7 = 2× Weak Accept + 1× Accept**(已收敛于 WA/Accept 边界)。
- 多位审稿人**亲自跑了 artifact**:数字逐条对得上 committed 文件,offline 命令 path-for-path 可解析(JAR 别名、trace 路径、rootApiKey 都对)。校准被反复评为 "exemplary / honest to a fault / no over-claim"。
- **通往稳 Accept 的唯一外部门槛:录 screencast**(`\todo{SCREENCAST-URL}` 待填,abstract + §7 各一处)。其余只剩 camera-ready 级别 nit。
- 你自己的估计:accept 概率从旧版 ~30-40% → 这版 **55-70%**(B 档 → A 档)。

---

## 8. Bottom line(再定位一句话)

**一个扎实的头条**(label-free / LLM-free 的 `HiddenDownstreamFailure` trace oracle,抓到别人看不见的 swallowed 跨服务失败,跨 HTTP/gRPC、离线可复现)**+ 诚实 scope 的支撑设计**(Root API Mode 设计、Sniper 设计保证+实测 7/9 覆盖、ResponseEnvelope opt-in fallible LLM)。

这是个**边界清晰、经得起审稿人亲自跑**的 tool demo——卖点是 detection of a new failure class,不是 param-attribution、不是野生 bug 语料、不是 generation 质量比拼(那些都诚实留给 companion research paper)。

---

## 9. 导航(东西在哪)

- **论文**:`paper/main_issta.tex`(4 页 sigconf + 1 页 refs);图 `paper/figures/{architecture,trace_oracle}.tex`;引用 `paper/refs.bib`。
- **头条 invariant 源码**:`mist-core/src/main/java/io/mist/core/oracle/shape/invariant/HiddenDownstreamFailureInvariant.java`。
- **离线 harness**:`evaluation/suts/bookinfo/OracleCheck.java`、`evaluation/suts/trainticket/ResponseEnvelopeLiveCheck.java`。
- **证据**:`docs/main-contribution/evidence/`(Bookinfo/Boutique/Sock Shop traces + 报告 + ResponseEnvelope transcript + 10/10 fault-detection 报告)。
- **整改全过程**:`debug/reviewer-remediation/{PLAN,EXPERIMENTS}.md`(7 轮评审 + 逐项 disposition + 真跑实验记录)。
