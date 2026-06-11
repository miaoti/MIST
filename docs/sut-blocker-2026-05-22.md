# SUT blocker — `train-ticket` 2026-05-22 admin-JWT 500 cluster

Captured during Task 37 (adaptive=true 10/10 verification). The blocker
is *not* a MIST defect — two SUT endpoints return a deterministic
HTTP 500 to authenticated requests but a healthy 403/4xx without auth,
which is why `SutHealthCheck` (currently no-auth) classifies them as
reachable.

## Evidence

```bash
JWT=$(curl -s -X POST http://129.62.148.112:32677/api/v1/users/login \
        -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"222222"}' \
      | jq -r '.data.token')

# (1) Vanilla GET — no auth: gateway returns 403 ⇒ "service is up"
curl -o /dev/null -w 'HTTP %{http_code}\n' \
     http://129.62.148.112:32677/api/v1/admintravelservice/admintravel
# HTTP 403

# (2) Authenticated GET — same URL, +Authorization: Bearer <JWT>:
curl -o /dev/null -w 'HTTP %{http_code}\n' \
     -H "Authorization: Bearer $JWT" \
     -H 'Content-Type: application/json' \
     http://129.62.148.112:32677/api/v1/admintravelservice/admintravel
# HTTP 500   ← deterministic, 3/3 retries identical
```

Same shape reproduces on `POST /api/v1/adminrouteservice/adminroute`.

## Broader audit (this date, this SUT)

| Verb | Path | No auth | + admin JWT |
|------|------|---------|-------------|
| GET  | `/admintravelservice/admintravel` | 403 | **500** ✗ |
| POST | `/adminrouteservice/adminroute`   | 403 | **500** ✗ |
| GET  | `/adminbasicservice/adminbasic/stations` | 403 | 200 ✓ |
| POST | `/contactservice/contacts` | 403 | 201 ✓ |
| POST | `/adminbasicservice/adminbasic/prices` | 403 | 400 ✓ (auth+validation OK) |
| POST | `/adminbasicservice/adminbasic/stations` | 403 | 200 ✓ |
| DELETE | `/admintravelservice/admintravel/1` | 403 | 400 ✓ (auth+validation OK) |
| POST | `/travel2service/admin_trip` | 403 | 405 ✓ (wrong verb, service up) |

Conclusion: 6/8 audited admin endpoints work fine under JWT; 2 are
**deterministically broken on the SUT side** when called with valid
admin auth.

## Documented fault → endpoint mapping (gates)

| Documented fault | Endpoint | This-date blocker |
|---|---|---|
| INVALID_CONTACTS_NAME | POST /contactservice/contacts | none |
| INVALID_SEAT_NUMBER | (seat POST) | none observed |
| INVALID_PRICE_RATE | POST /adminbasic/prices | none |
| **INVALID_ROUTE_ID** | POST /adminroute | **SUT 500** |
| INVALID_STATION_NAME | POST /adminbasic/stations | none |
| INVALID_STATION_LENGTH | POST /adminbasic/stations | none |
| **INVALID_TRIP_ID_FORMAT** | DELETE /admintravel/{id} | DELETE works, but SmartInputFetcher seeds tripId via `GET /admintravel` ⇒ that GET is the 500 endpoint |
| **INVALID_TRIP_ID_LENGTH** | DELETE /admintravel/{id} | (same as above) |
| INSUFFICIENT_STATIONS | POST /travel2/admin_trip | 405 (probably wrong verb in probe — actual MIST call may differ) |
| INVALID_STATION_NAME_LENGTH | POST /adminbasic/stations | none |

At least 1 documented fault (`INVALID_ROUTE_ID`) is hard-blocked. The
two `INVALID_TRIP_ID_*` faults depend on whether MIST's tripId pool
can be seeded without `GET /admintravel`.

## Why the preflight didn't catch this

`SutHealthCheck.httpClientProbe(timeoutMs)` (mist-core/.../health/SutHealthCheck.java)
sends an *unauthenticated* request. Design intent was "is the network
path open?". For "is the endpoint usable under the auth shape MIST
actually uses?" we need an *authenticated* preflight variant. That's
filed as a follow-up but not yet implemented (would need to plumb the
auth handler into the runner before the preflight call site).

## How 10/10 was achieved historically

Run 10 + Run 12 both hit 10/10 with adaptive=false **when the SUT was
healthy** (no admin-JWT 500s anywhere). The detection counts are
faithful to MIST's design; they degrade strictly with SUT health on
the specific endpoints each fault depends on.
