# Probe — 命门 A: intent-conditioned attribution(可量化、抗 ablation?)

> 探测日期 2026-06-01。方法:两个独立 agent 双重认证(代码逻辑 vs trace 数据本身)+ 手动复验。
> 结论先行:**判定 = 居于 a/b 之间的 (b′)** —— TARGET_REJECTION=0 **不是信息论不可得,而是代码没用上已有信息**;但能补救的部分恰好**不服务于论文要 claim 的 trace-only 新类**。attribution **不能**作为承重的 method 新颖点。

---

## 1. attribution 算法实况(file:line 取证)

入口 `TraceAttribution.attribute(trace, targetService, targetParam)`(`mist-core/.../oracle/attribution/TraceAttribution.java:42-62`),4 步漏斗:

1. `LeafErrorSpanFinder.findLeafError(trace)`(`:45`):从 error-tagged root 沿"只进 error 子节点"DFS,返回**最深的 error span**。error 判据 = `otelStatus=="ERROR"` 或 `httpStatus>=400`(`LeafErrorSpanFinder.java:88-92`)。
2. 无 error span → `NO_ATTRIBUTION`(`:46`)。
3. `serviceMatches(leaf.service, targetService)`(`:48`,小写相等或子串):不同服务 → `UPSTREAM_REJECTION`(`:49`)。
4. 同服务才比 param:`targetParam` 空 → 服务级 `TARGET_REJECTION`(`:55-57`);否则 `MethodToParamMapper.isResponsibleFor(leaf.operation, targetParam)`(`:59`):命中 → `TARGET_REJECTION`,否则 `WRONG_PARAM_REJECTION`(`:60-61`)。

**param 级判定全部压在一行:`TraceAttribution.java:59` 的 token 重叠**。它只喂 `leaf.operation`,**从不读 `Span.tags` 或 `Span.logs`**。

## 2. 为什么 TT 上 TARGET_REJECTION=0(确切原因)

target 上下文**接线正确**(非 bug):`__targetParam`/`__targetService` 在 `MultiServiceRESTAssuredWriter.java:1346-1365` 正确解析并流入 `oracle.evaluate(...)`→`TargetAttributionInvariant`→`attribute()`。真名(如 `startStation`)确实到达 `isResponsibleFor`。

失败发生在 token 重叠这一步,因为 **TT 的 leaf error span 的 operationName 是通用 controller / 裸 HTTP verb**。实测 `admin_add_route_failed.json` 的 error span operationName:
```
RouteController.createAndModifyRoute   (ts-route-service, otel ERROR)
AdminRouteController.addRoute          (ts-admin-route-service, otel ERROR)
POST /api/v1/routeservice/routes       (500)
```
`createAndModifyRoute`→{create,modify,route};param `startStation`→{start,station};**零重叠 → 永远 WRONG_PARAM / UPSTREAM,从不 TARGET**。`MethodToParamMapper.java:8-33` 的 docstring **自己就预言了这个退化**,并点名 `RouteController.createAndModifyRoute`:只有 tier-2(naming heuristic)实现,tier-1(OpenAPI `x-mist-param-validator-method`)、tier-3(probe cache)均**未实现**。

## 3. 双重认证的关键分歧 → 裁决

- **代码 agent**:`attribute()` 只读 service+operation,据此判"param 级信息论不可得"。
- **数据 agent**:同一个 `admin_add_route_failed.json` 的 error span `logs[].exception.message = "For input string: \" 11\""`(`NumberFormatException`)+ stacktrace 指到 `RouteServiceImpl.java:45`。**坏值 `" 11"` 就在 trace 里**;路径参数值也总在 `http.url`/`http.target`。

**手动复验**(multiline-safe 解析 `admin_add_route_failed.json`):16 spans,`http.status_code` = {500:5, 200:2},`otel.status_code` = {ERROR:7},`exception.message` = `For input string: " 11"`。**裁决:数据 agent 正确——TARGET_REJECTION=0 是代码限制,不是信息论限制。** trace 携带的信号(exception message 回显坏值、stacktrace 钉到代码行、路径值明文)比代码用到的多。

## 4. 判定(a / b / b′)

| 子类 | param 级归因可得? | 依据 |
|---|---|---|
| 路径变量参数 | **可得(明文)** | `http.url`/`http.target` 总在 span |
| body 参数 + 抛值携带异常(如 NumberFormatException) | **可得(回显值匹配)** | `logs[].exception.message` 含注入值;MIST 自己知道注入的 (param,value),value 匹配即可定 param |
| body 参数 + 通用空消息 500 | **仅 endpoint 级** | body 不入 span(只 `http.request_content_length`),SQL 参数化为 `?`,下游 message="" |
| **优雅 soft 拒绝(200 + status:0,论文头条)** | **不可得** | 不抛异常、不记 reason;TT 全语料**零** business-rule 文本消息 |

**判定 = (b′)**:既非纯 (a) 也非纯 (b)。可用 value-matching 把 attribution 从 service 级提到 param 级,**但只对"响亮"的硬失败/路径参数有效**;而响亮失败本就在 response 里可见、**不是论文要 claim 的 trace-only 新类**。对真正 trace-only 的优雅 soft 拒绝,当前 TT 仪表下信号缺失,param 级归因**不可得**。

## 5. 对 A-main 的含义

- **attribution 不能当承重 method 新颖点**:能做强的部分(value-match 硬失败)是工程、且不服务新类;服务新类的部分做不了。
- 诚实的天花板 = **service-level attribution**(TARGET-at-service vs UPSTREAM),已可用。
- 若要 param 级服务新类,必须**先改证据**(更富仪表:把被拒字段/值写进 span tag 或 error message),即 attribution 要先有"仪表贡献"垫底——这超出当前 trace。
- **决定性 file:line:`TraceAttribution.java:59`**(整个 param 判定,仅喂 `leaf.operation`);`MethodToParamMapper.java:27-32` 自述退化案例点名了实测到的那个 span。

**置信度:高(≈0.9)**,基于代码追踪 + 真实失败 trace 的逐 span 复验。
