# Probe — 命门 B: 除 TrainTicket 外有无可挖的野生(commit 历史)被吞-下游 / silent-accept bug 语料

> 探测日期 2026-06-01。方法:web research agent(GitHub issue/PR/commit 检索 + 学术数据集核查 + 逐条可复现性判定)。
> **判定 = (C)**:trace-only 两类(swallowed-downstream / silent-acceptance)的**可复现野生 bug 语料基本不存在**;**个体可复现野生 bug 计数 = 0**。study 腿只能靠 注入/复制故障 + 引文级 prevalence(Uber/Yuan)+ 自测 prevalence,**不能** claim 媲美 AGORA+ 的 32 真实 bug。

---

## 1. 野生 bug 逐条核查(全部不达标)

| 候选 | URL | 类 | 复现判定 |
|---|---|---|---|
| Spring Cloud Gateway #2713(下游 timeout,网关却回 200) | github.com/spring-cloud/spring-cloud-gateway/issues/2713 | (i) | **轶事/不可复现**:维护者 "I am unable to reproduce this. I get a 504." 无 repro、无 fix commit、stale 关闭。最佳概念匹配,但塌了。 |
| Istio #48293(upstream 不健康仍回 200) | github.com/istio/istio/discussions/48293 | (i)? | **非 bug**:gRPC 协议 response code 恒 200,维护者确认是预期。 |
| Ocelot #603 | github.com/ThreeMammals/Ocelot/issues/603 | (i)? | **非 bug — feature request**:本就抛 SocketException,用户只想要更漂亮的 502。 |
| Spring Cloud Gateway #181 | github.com/spring-cloud/spring-cloud-gateway/issues/181 | — | **与吞相反**:仍暴露 500,只是丢了 body。 |

**GitHub commit 搜索全空**:`swallow exception return 200`、`silently accept invalid validation reject`、`mask downstream error return success` 均 0 结果。这两类 bug 修复时藏在通用 "fix error handling"/"improve validation" commit 里,**无机器可挖信号**。**应用级 class-(ii) silent-acceptance 可复现野生 bug:一条都没有。**

## 2. 数据集 / 研究(可复用 ground truth?)

| 来源 | 是什么 | 可复现 (i)/(ii) 野生 bug? |
|---|---|---|
| **Uber《Tale of Errors》SIGMETRICS'25**(DOI 10.1145/3700436;artifact zenodo.org/records/13947828) | 11B+ RPC、6000+ 服务:**~29% 成功(2xx)请求藏下游非致命错误**;放出 ~1.4M 匿名 trace | **仅聚合 prevalence**——正是你要的那句话。无个体可复现 bug。**prevalence study 的金子,野生语料无用。** |
| **Yuan《Simple Testing…》OSDI'14** | 198 真实失败:92% 灾难性失败 = 错误处理显式信号的非致命错误(吞/空 catch) | 精神最接近 class (i),且是真实 OSS bug;但**在分布式数据系统、非 request/response 微服务**,目录是失败案例+静态 bug 模式,**未打包成可重放 trace 场景**。只能当 motivation 引。 |
| **RCAEval**(github.com/phamquiluan/RCAEval) | 735 failure case、11 故障型、metrics+logs+traces | **注入 chaos 故障**;ground truth = 根因服务/指标定位,非"伪成功"检测。非野生。 |
| **Nezha**(github.com/IntelligentDDS/Nezha) | Online Boutique + TrainTicket RCA | **注入故障**;`return`/`exception` 是注入点→标注根因服务做定位。非野生。 |
| **TrainTicket F1–F22 / train-ticket-fault-replicate** | 22 条工业调研复制故障 | **全部注入/复制**(F6 retry-timeout、F8 dropped token、F10 误调用、F20 枚举不匹配 近 (i)/(ii)),但**非原仓库未改动的生产 bug**。可作"工业接地的注入场景"。 |
| **微服务 issue 实证研究**(arXiv 2302.01894,15 系统 2641 issue) | 异常处理/服务通信为 top 类,有标注 issue 复制包 | 聚合分类+计数;点名例子非 (i)/(ii) trace-only。**复制包是唯一值得手挖的地方**,但类目不隔离"2xx 掩 5xx"。 |
| **AGORA+(对标),TOSEM'25**(DOI 10.1145/3726524) | 32 真实 bug | **关键 framing**:其 32 bug 是**对 LIVE 商业 API**(Amadeus/DB/GitHub/Marvel/NYT/YouTube)的**单响应 output-invariant 违规**(如"0 床位的房间"),**非 trace-only、非挖 OSS commit**。可信度来自**打公开生产 API**——这条路对 trace-only 类**不可用**(你得拥有部署才能看下游 span)。 |

## 3. 为什么野生语料结构性地挖不到

1. **定义上边界不可见**:你要求只能靠跨服务 trace 检出 → OSS bug 报告由观察 client 响应的人提交,这类 bug 系统性**漏报**,且报了也缺跨服务证据去标注。
2. **修复无标签**:藏在通用 commit 里,无可挖信号。
3. **唯一可证为真的地方是内部遥测**(Uber 29%),但匿名聚合、无逐 bug 可复现、无源码。
4. **AGORA+ 的逃生口(打公开 API)不可迁移**:trace-only 类必须自有部署看下游 span。

## 4. 对 A-main 的含义(策略)

- **不要 claim** 媲美 AGORA+ 32 的野生 bug 语料 —— 无法支撑 ≥5 条个体可复现 trace-only (i)/(ii),硬 claim 会招致致命 rebuttal。
- **可被 reviewer 接受的双论证**:
  - **(a) prevalence 由引文确立**:Uber SIGMETRICS'25(29% 的 2xx 藏下游错误,11B RPC)+ Yuan OSDI'14(92% 灾难性失败 = 误处理非致命错误)→ 证明两类在生产真实、普遍、高危。
  - **(b) 评测用注入/复制故障 + 自测 prevalence**:注入场景取自 train-ticket-fault-replicate(工业接地),并在你掌控的 SUT 上**自跑一次 prevalence 测量**(镜像 Uber 的指标)。把"只注入"升级为"注入 + 工业接地 + prevalence 结果"。
- **末路手挖**(reviewer 硬要 ≥1 真 bug 时):最高产是 arXiv 2302.01894 的标注 issue 复制包 + 重读 OSDI'14 Aspirator 确认的吞错误 bug,看是否落在仍可构建的 request/response 服务里。成功率**低**。

**关键 URL**:Uber dl.acm.org/doi/10.1145/3700436(artifact zenodo.org/records/13947828);Yuan usenix.org/system/files/conference/osdi14/osdi14-paper-yuan.pdf;AGORA+ dl.acm.org/doi/10.1145/3726524;TrainTicket faults github.com/FudanSELab/train-ticket/wiki/Fault-Description + /train-ticket-fault-replicate;RCAEval github.com/phamquiluan/RCAEval;Nezha github.com/IntelligentDDS/Nezha;微服务 issue 研究 arxiv.org/abs/2302.01894。
