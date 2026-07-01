# PREP (main-track) — P3: broker-async write-path resolution for the B2.4 benign trap

> **Status:** main-track prep (no tool code). **Method:** read-only static research on the TrainTicket SUT source; nothing in the SUT repo was modified. **Date:** 2026-07-01.
> **Question answered:** does TrainTicket contain a client-facing write whose 2xx acknowledgement precedes a RabbitMQ-mediated persist that a black-box read-back GET can eventually observe (the "benign trap" needed to measure the differential data-integrity oracle's false-positive rate under broker-degraded quiescence)?
> **Short answer:** no such path exists as-is (verdict **b — NEW-INJECTOR-NEEDED**). The broker and one real produce→consume edge exist and are deployed, but the only broker-mediated persist (`Delivery`) has **no HTTP read-back anywhere in the system**, and the only edge whose consumer's persist would pair with a write endpoint (`email`/NotifyInfo) has its production call site **commented out**. A small, flag-gated, LOST_WRITE-style SUT-side change (~40 lines in `ts-food-service`) closes the gap.

All SUT paths below are relative to `C:\Users\miaot\Github\train-ticket-injection` unless noted. Line numbers are from the checked-out `MIST-trainticket` branch at `bbf3d6ae`.

---

## 1. Environment / branch check

| Check | Result | Evidence |
|---|---|---|
| Checked-out branch | `MIST-trainticket` (working tree clean) | `git branch -a` → `* MIST-trainticket`; `git status --short` empty |
| HEAD | `bbf3d6ae` "feat(adminbasic): add opt-in LOST_WRITE_FAULT …" | `git log --oneline -5` |
| Which branch has the LOST_WRITE injector | **Only `MIST-trainticket`.** `git grep -l "LOST_WRITE_FAULT\|mist.fault.lostwrite.enabled" MIST-trainticket` hits 4 files (`ts-admin-basic-info-service/.../AdminBasicInfoServiceImpl.java`, `ts-admin-route-service/.../AdminRouteServiceImpl.java`, plus the two `FAULT_INJECTION_SUMMARY.md`); the same grep against `origin/injection` returns **nothing** | command output, 2026-07-01 |
| Relationship of branches | `origin/injection` (tip `54e1a2e5`) **is an ancestor** of `MIST-trainticket`; MIST-trainticket = injection + the two LOST_WRITE commits (`5c471dd8`, `bbf3d6ae`) | `git merge-base --is-ancestor origin/injection MIST-trainticket` → true |
| Matches smoke doc? | Yes: deployed images were built from `MIST-trainticket`, and that is exactly the branch checked out locally. Note there is **no local `injection` branch**, only `remotes/origin/injection` | `git branch -a` |
| Do local source changes reach the cluster? | Yes. `make deploy` = `build` + `deploy-no-build` (`Makefile:26-28`); `hack/build-image.sh` docker-builds **every** `ts-*` dir with a Dockerfile and tags it `codewisdom/ts-*:1.0.2` (`hack/build-image.sh:7-14`, `Makefile:3-4`), which is the exact image name `deploy.yaml` references (46 `image: codewisdom/...` entries) | files cited |

---

## 2. RabbitMQ inventory

### 2.1 Services with Spring AMQP on the classpath

Exactly **5** services declare the amqp dependency (`grep spring-boot-starter-amqp **/pom.xml`): `ts-preserve-service`, `ts-preserve-other-service`, `ts-notification-service`, `ts-food-service`, `ts-delivery-service`. A repo-wide grep for `RabbitTemplate|@RabbitListener|convertAndSend` hits only 6 files, all in those 5 services. No Spring Cloud Stream anywhere (`grep -rln "EnableBinding\|StreamListener\|spring-cloud-stream" ts-*/src/main` → empty). `ts-rebook-service`, `ts-order-service`, `ts-order-other-service` have rabbit config **commented out** in `application.yml` (e.g. `ts-rebook-service/src/main/resources/application.yml:14-15`) and no rabbit code. `ts-food-delivery-service` (a different service from `ts-delivery-service`) is REST-only — no rabbit at all.

### 2.2 Producer → queue → consumer edges

| # | Queue | Producer (file:line) | Trigger | Consumer (file:line) | What the consumer persists |
|---|---|---|---|---|---|
| E1 | `email` | `ts-preserve-service/src/main/java/preserve/mq/RabbitSend.java:20` (`convertAndSend(Queues.queueName, val)`; queue name `"email"` at `preserve/config/Queues.java:10`) | `PreserveServiceImpl.sendEmail()` (`:288-299`) — **its only call site is commented out** at `PreserveServiceImpl.java:260-261` (`// TODO: change to async message serivce` / `// sendEmail(notifyInfo, headers);`) | `ts-notification-service/src/main/java/notification/mq/RabbitReceive.java:51-91` (`@RabbitListener(queues = "email")`) | Sends SMTP mail, then **always** saves a `NotifyInfo` row (`notifyRepository.save(info)` at `RabbitReceive.java:90`, even on mail failure — `sendStatus=false`) |
| E2 | `email` | `ts-preserve-other-service/src/main/java/preserveOther/mq/RabbitSend.java:22` | `PreserveOtherServiceImpl.sendEmail()` (`:287-`) — call site **also commented out** at `PreserveOtherServiceImpl.java:260` | same as E1 | same as E1 |
| E3 | `email` | `ts-notification-service/src/main/java/notification/mq/RabbitSend.java:20` | **Test stub only:** `GET /api/v1/notifyservice/test_send_mq` sends the literal string `"test"` (`NotificationController.java:33-37`) | same as E1 | none — `JsonUtils.json2Object("test", NotifyInfo.class)` returns `null` (`ts-common/.../JsonUtils.java:58-69` catches and returns null), consumer logs and returns before the save (`RabbitReceive.java:55-58`) |
| E4 | `food_delivery` | `ts-food-service/src/main/java/foodsearch/mq/RabbitSend.java:21` (queue name `"food_delivery"` at `foodsearch/config/Queues.java:10`); called from `FoodServiceImpl.createFoodOrder` (`:143`), `FoodServiceImpl.createFoodOrdersInBatch` (`:104`), and test stub `GET /foodservice/test_send_delivery` (`FoodController.java:37-48`) | `POST /api/v1/foodservice/orders` and `POST /api/v1/foodservice/createOrderBatch` (`FoodController.java:56-66`) | `ts-delivery-service/src/main/java/delivery/mq/RabbitReceive.java:35-55` (`@RabbitListener(queues = "food_delivery")`) | `deliveryRepository.save(delivery)` at `RabbitReceive.java:50` — a `Delivery` row (id, orderId, foodName, storeName, stationName; `delivery/entity/Delivery.java:21-35`) in the **ts-delivery-mysql** DB |

**Bottom line of the inventory:** in the code actually deployed, the `email` queue has **zero real producers** (E1/E2 dead code, E3 unparseable test payload), and the one live edge (E4) persists into a service that has **no controller at all** — `ts-delivery-service/src/main/java/` contains only `Queues.java`, `DeliveryApplication.java`, `Delivery.java`, `RabbitReceive.java`, `DeliveryRepository.java` (directory listing). A repo-wide grep confirms `DeliveryRepository`/`NotifyRepository` are referenced **only** by their own service's consumer/repository files — no HTTP endpoint anywhere reads either table, and the gateway has no `deliveryservice` route (`ts-gateway-service/src/main/resources/application.yml` — only `fooddeliveryservice` at `:99`, which is the unrelated REST-only service).

### 2.3 Is the broker deployed in the `make deploy` topology? — YES

- `make deploy` → `hack/deploy/deploy.sh` `quick_start` → `deploy_infrastructures` (`hack/deploy/deploy.sh:18-25`), which **always** runs `helm install rabbitmq deployment/kubernetes-manifests/quickstart-k8s/charts/rabbitmq` and waits for rollout (`hack/deploy/utils.sh:41-44`; release name `rabbitmq` at `utils.sh:22`, chart path at `utils.sh:9`).
- The chart creates a 1-replica `codewisdom/rabbitmq:3` Deployment on port 5672 (`charts/rabbitmq/values.yaml`, `templates/deployment.yaml`) plus a ConfigMap named `rabbitmq` with `rabbitmq_host: rabbitmq` (`templates/configmap.yaml`).
- `deploy.yaml` injects that ConfigMap via `envFrom` into **8** services: ts-delivery (`yamls/deploy.yaml:628`), ts-food (`:716`), ts-notification (`:936`), ts-order-other (`:982`), ts-order (`:1028`), ts-preserve-other (`:1116`), ts-preserve (`:1160`), ts-rebook (`:1248`) — matching each service's `spring.rabbitmq.host: ${rabbitmq_host:localhost}` (e.g. `ts-food-service/src/main/resources/application.yml:22-23`).
- **Caveat (unverified live):** this is what the manifests/scripts guarantee for run22's `make deploy`; I did not query the running cluster from here. Before building B2.4, confirm with `kubectl get pods | grep rabbitmq` in the WSL2 minikube.

---

## 3. Candidate async write paths — analysis

The plan named four candidates. Verdict per candidate:

### 3.1 Preserve (ticket booking) — `POST /api/v1/preserveservice/preserve` — NOT a broker path (async part is dead code)

- Endpoint: `ts-preserve-service/.../controller` maps `@RequestMapping("/api/v1/preserveservice")` + `@PostMapping("/preserve")` (controller `:18`, `:32`); requires role USER/ADMIN (`preserve/config/SecurityConfig.java:71`), gateway route exists.
- Synchronous part (before 2xx): the whole booking — order create via ts-order REST, seat dispatch, optional food order (step 6 REST-POSTs `/api/v1/foodservice/orders`, `PreserveServiceImpl.java:406-415`, invoked at `:206`), optional consign (step 7).
- Broker part: step 8 builds a `NotifyInfo` and then… **does not send it** — `// sendEmail(notifyInfo, headers);` is commented out (`PreserveServiceImpl.java:260-261`). The rabbit hop in preserve is dead code on this branch (same in preserve-other, `PreserveOtherServiceImpl.java:260`).
- Even if re-enabled: the consumer's persist is a `NotifyInfo` row with **no GET anywhere** (see 3.3). The primary entity (Order) is persisted synchronously and its read-back reflects immediately — no trap.
- **Judgment: does not qualify** (2xx is after the real persist; the async leg is dead and unobservable anyway). But note: a preserve *with food* transitively fires the one live broker edge E4.

### 3.2 Food order — `POST /api/v1/foodservice/orders` — real broker hop, but the async persist is UNOBSERVABLE

- (a) Endpoint: `POST /api/v1/foodservice/orders` (`FoodController.java:56-60`), gateway route `Path=/api/v1/foodservice/**` → `ts-food-service` (`ts-gateway-service/src/main/resources/application.yml:102-105`).
- (b) Synchronous part before 2xx: duplicate check, then **`foodOrderRepository.save(fo)`** — the FoodOrder row is persisted synchronously (`FoodServiceImpl.java:131`) — then the `Delivery` JSON is enqueued on `food_delivery` (`:143`, exceptions swallowed at `:144-146`), then 2xx with the saved entity.
- (c) What the consumer persists afterward: a `Delivery` row in ts-delivery-mysql (`delivery/mq/RabbitReceive.java:50`).
- (d) Read-back for the async persist: **NONE.** ts-delivery-service has no controller (only 5 source files, listed in §2.2); no other service reads `DeliveryRepository`; no gateway route. The only read-backs that exist (`GET /api/v1/foodservice/orders/{orderId}`, `FoodController.java:82-86`; `GET /orders`, `:50-54`) observe the **synchronously** saved FoodOrder, which is already visible when the 2xx arrives.
- (e) Black-box exercisable: yes — route exists; food's Spring Security chain puts `.antMatchers("/api/v1/foodservice/**").permitAll()` **first** (`foodsearch/config/SecurityConfig.java:71`), which first-match-shadows the admin-role rules for POST/PUT/DELETE at `:72-74`, so an ordinary logged-in user (indeed, any caller) can POST. (Caveat: that shadowing is my reading of Spring Security first-match semantics; the Gate-1 smoke already exercised similar TT endpoints without admin issues, but verify once live.)
- (f) Produce: `FoodServiceImpl.java:143` / `foodsearch/mq/RabbitSend.java:21`. Consume: `delivery/mq/RabbitReceive.java:35-55`.
- **Judgment: does NOT qualify as-is.** The 2xx is *not* ack-before-persist for anything a GET can see: the observable entity is persisted before the ack; the broker-persisted entity is invisible to HTTP. This is exactly the "rabbit used only for a side-effect with no read-back" disqualifier — except the side-effect here *is* a DB row (unlike email), which is why it is the best raw material for a fix (§4).

### 3.3 Notification flows — `POST /api/v1/notifyservice/notification/*` — synchronous, no persist at all

- Endpoints: `preserve_success`, `order_create_success`, `order_changed_success`, `order_cancel_success` (`NotificationController.java:57-75`), permitAll (`notification/config/SecurityConfig.java:71`).
- The handlers call `NotificationServiceImpl.*` which **synchronously** renders and SMTP-sends mail and returns a bare boolean — no rabbit, no repository write anywhere in the sync path (`NotificationServiceImpl.java:37-136`; the class doesn't even reference `NotifyRepository`).
- The service's rabbit *consumer* (E1) does save `NotifyInfo` rows, but no producer feeds it real data (§2.2) and **no controller endpoint reads `NotifyRepository`** (`NotificationController.java` full listing: welcome/test_send_mq/test_send_mail + the four POSTs; repo-wide grep confirms no other reader).
- **Judgment: does not qualify** — a 2xx here acknowledges an email attempt, not a persist; nothing to read back.

### 3.4 Order cancel — `POST /api/v1/cancelservice/cancel/...` — synchronous REST end-to-end

- Cancel's order-status mutation happens via synchronous REST to ts-order; its "notification" is a synchronous REST POST to `/api/v1/notifyservice/notification/order_cancel_success` (`ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:147-152`), which per §3.3 sends mail synchronously and persists nothing. `ts-cancel-service` has no amqp dependency.
- **Judgment: does not qualify** — no broker hop anywhere in the flow.

### 3.5 Payment — no rabbit

`grep -rln "rabbit|amqp" ts-payment-service/src ts-inside-payment-service/src` → empty. **Does not qualify.**

### 3.6 CRITICAL judgment (Q3), consolidated

For every client-facing 2xx write in TrainTicket, the observable persist is synchronous. The **only** broker-mediated persist in the deployed system is the `Delivery` row (edge E4), and it is unobservable black-box: no controller, no route, no cross-service reader. The `NotifyInfo` persist (edge E1) is doubly disqualified: its real producers are commented out **and** it has no reader. Therefore **no existing path is ack-before-persist with an eventually-consistent read-back**. Verdict (a) EXISTING-PATH is off the table on the evidence above.

---

## 4. Verdict and recommendation for B2.4

### Verdict: **(b) NEW-INJECTOR-NEEDED** — and it is small, because all the hard parts already exist

The broker is deployed by every `make deploy` (§2.3); `ts-food-service` already has the amqp dependency, a working connection, a producer, a queue-declaration pattern, and a same-entity read-back GET; `ts-delivery-service` already demonstrates the ~30-line consumer pattern. What is missing is only the *coupling*: an observable entity whose persist rides the broker.

### Recommended: Option A — flag-gated ASYNC_WRITE (benign trap) inside `ts-food-service`

Make the FoodOrder persist itself broker-mediated when a MIST flag is on:

1. **New queue name** in `ts-food-service/src/main/java/foodsearch/config/Queues.java` (e.g. `food_order_async`, declared as a second `Queue` bean beside the existing one at `:10-15`).
2. **Flag-gated branch** in `FoodServiceImpl.createFoodOrder` (`ts-food-service/src/main/java/foodsearch/service/FoodServiceImpl.java:114-150`): when `mist.trap.asyncwrite.enabled` (default **false**; same `@Value` + `JAVA_TOOL_OPTIONS -D` opt-in pattern as the existing LOST_WRITE injector, `ts-admin-route-service/.../AdminRouteServiceImpl.java:31-34` — the smoke already proved `-D` flags are the reliable enablement route on TT), **skip** the synchronous `foodOrderRepository.save(fo)` at `:131` and instead `convertAndSend` the FoodOrder JSON to `food_order_async`; still return the normal `Response<>(1, success, fo)`.
3. **New ~30-line consumer** `ts-food-service/src/main/java/foodsearch/mq/RabbitReceiveOrder.java` modeled on `delivery/mq/RabbitReceive.java:24-56`: `@RabbitListener(queues = "food_order_async")` → deserialize → `foodOrderRepository.save`.
4. **Trap semantics achieved:** `POST /api/v1/foodservice/orders` returns 2xx **before** the FoodOrder row exists; the persist happens after a *real* RabbitMQ hop; the oracle's natural same-entity read-back `GET /api/v1/foodservice/orders/{orderId}` (`FoodController.java:82-86`) eventually flips from `status:0 "Order Id Is Non-Existent"` (HTTP 200 either way — the differential is in the body) to `status:1` with the entity. Flag off ⇒ byte-identical stock behavior. Producer and consumer sit in the same service, but the message still transits the deployed broker pod, so trace-driven quiescence still hits the span-link degradation this trap must exercise.
5. Exercisable black-box through the gateway with an ordinary user token (§3.2e); also reachable transitively via a preserve-with-food booking (`PreserveServiceImpl.java:206`, `:406-415`) if B2.4 wants the trap embedded in a realistic user journey.

Why A over the alternative: the oracle as designed does same-resource read-backs after a 2xx write; only a lagging **primary-entity** persist traps it. It also matches the methodology precedent — an opt-in, per-deploy, SUT-side variant exactly like LOST_WRITE (report it in the paper as an instrumented benign trap, mirroring `FAULT_INJECTION_SUMMARY.md`).

### Complementary (cheap, keeps the write path 100% stock): Option B — observation endpoint on `ts-delivery-service`

Add a read-only `DeliveryController` (`GET /api/v1/deliveryservice/delivery/{orderId}` → `deliveryRepository.findByOrderId`, `delivery/repository/DeliveryRepository.java:19`) plus a gateway route (k8s Service already exists, `yamls/svc.yaml:158`). Then the untouched production write `POST /api/v1/foodservice/orders` already *is* an async trap — but only for an oracle that knows the cross-service postcondition "food order ⇒ delivery row". Use B if/when B2.4 wants a trap with zero write-path modification and the oracle grows cross-entity read-back mappings; otherwise A is the one that actually exercises the FP measurement.

### Not recommended

Reviving the commented-out preserve→email send (`PreserveServiceImpl.java:261`): the consumer's `NotifyInfo` persist would still need a brand-new read endpoint **and** depends on mail-service behavior; strictly more moving parts than A for the same broker hop.

### Honest uncertainty ledger

- Static analysis only; no service was run for this doc. The E4 edge (food→delivery over `food_delivery`) is consistent end-to-end in source and manifests but was not observed live here.
- Live presence of the rabbitmq pod in run22's cluster: guaranteed by `deploy.sh` code path, not re-verified against the cluster (one `kubectl` command before B2.4 work).
- The Spring Security first-match shadowing at `foodsearch/config/SecurityConfig.java:71-74` (POST effectively permitAll) is high-confidence but should be confirmed with one authenticated + one unauthenticated live POST.
- How the OTel javaagent models the rabbit hop (span link vs. remote parent) was **not** verified in this repo — the branch's own tracing bits are SkyWalking-oriented (`Makefile:34`); the span-link degradation premise comes from the MIST plan, not from SUT source, and should be confirmed from an actual trace during B2.4 bring-up.
