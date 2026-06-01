# DummyJSON Auth API — Performance Test Report

**Target:** `https://dummyjson.com/auth` (Login → Get current user → Refresh session)
**Tool:** Gatling 3.15.0 · Closed model · Run A: 20 users · Run B: 40 users · 40 s ramp + ~3 min hold

---

## 1. Runs Compared

| | Run A | Run B |
|---|---|---|
| Report ID | `auth-20260601014657072` | `auth-20260601021943119` |
| **Peak concurrent users** | **20** | **40** |
| Ramp-up | 0 → 20 over 40 s | 0 → 40 over 40 s |
| Hold (steady load) | ~3 min | ~3 min |
| Total duration | 3m 44s | 3m 43s |

> The two runs differ: Run B applies **double** the concurrency of Run A. This is a **load-scaling comparison**.

---

## 2. Summary — Global (All Requests)

| Metric | Run A (20 users) | Run B (40 users) |
|---|---|---|
| Total requests | 2,514 | 2,484 |
| **Throughput** (req/s) | **11.17** | **11.09** |
| **Error rate** | **0.00%** (0 KO) | **0.00%** (0 KO) |
| **Mean (avg)** | **284 ms** | **280 ms** |
| **P95** | **458 ms** | **430 ms** |
| P50 (median) | 134 ms | 114 ms |
| P75 | 249 ms | 245 ms |
| P99 | 2,844 ms | 3,177 ms |
| Max | 35,335 ms | 39,747 ms |
| Std Dev | 1,125 ms | 1,321 ms |




---

## 3. Observed Facts (Run A vs Run B)

- **Run B applied 2× the concurrent load** of Run A (40 vs 20 users); everything else was identical.
- **Throughput was flat** despite the doubled load: 11.17 → 11.09 req/s. 
- **Both runs passed with 0 errors.**
- **Central response times barely moved and were marginally *better* in Run B:** median 134 → 114 ms, P95 458 → 430 ms, mean 284 → 280 ms.
- **The extreme tail got worse under the higher load:** P99 2,844 → 3,177 ms, Max ~35.3 s → ~39.7 s, Std Dev 1,125 → 1,321 ms.
- The tail lives in the **`/me` and `/refresh`** endpoints (medians ~106–113 ms but P99 of 4–6 s and Max of 12–30 s). Login stayed tight in both runs.

---

## 4. Bottleneck Hypotheses (not confirmed)

- **Primary signal — a throughput ceiling.** Doubling concurrent users (20 → 40) produced **no increase in throughput** (~11 req/s both) and even slightly fewer completed iterations. That is the classic signature of a **server-side / rate-limit ceiling on the shared public API**: extra clients queue and wait rather than completing more work.
- Because **central latency stayed flat while only the extreme tail grew**, the added load appears to surface as occasional long stalls (a few multi-second outliers) rather than a uniform slowdown — consistent with upstream throttling or connection-level limits (Cloudflare / dummyjson), not client-side saturation at these modest user counts.
- The **HTTP 429 (rate limiting)** and **SSL-handshake timeouts** seen in earlier, more aggressive runs reinforce that the limit is **server/policy-imposed**, not a property of the script or the load generator.

These remain hypotheses; confirming them would need server-side metrics we do not have for a public API.

---

## 5. Limitations & Public-Environment Factors

- **Public, shared API behind Cloudflare.** No control over server capacity, co-tenant traffic, or throttling — the most likely cause of the flat throughput and the latency tail.
- **Throughput is server-bounded, not client-bounded here.** Above the ceiling, raising the user count (as Run B did) does not raise throughput, so concurrency is not the controlling variable for these two runs.
- **Modest scale.** Peak 20 (Run A) and 40 (Run B) concurrent users — a baseline/scaling check, not production-scale load.

---

*Source: Gatling HTML reports `auth-20260601014657072` (20 users) and `auth-20260601021943119` (40 users) — Global stats tables.*
