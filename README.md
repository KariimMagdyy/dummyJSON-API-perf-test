# DummyJSON Auth API — Performance Test

A [Gatling](https://gatling.io) load test for the authentication flow of the public
[DummyJSON](https://dummyjson.com) API. It logs a user in, calls the authenticated
"current user" endpoint, then refreshes the session — under a configurable concurrent load —
and produces an HTML performance report.

> **Note:** the target is a shared **public** demo API. Results reflect that environment
> (rate limiting, Cloudflare, internet latency), so treat them as a methodology showcase
> rather than an absolute capacity benchmark.

---

## Scenario under test

The simulation (`Auth.java`) runs a single **"Auth Flow"** scenario per virtual user:

| # | Step | Request | Notes |
|---|------|---------|-------|
| 1 | **Login** | `POST /auth/login` | Reads credentials from the feeder; saves `accessToken` + `refreshToken`. Stops the user if it fails (`exitHereIfFailed`). |
| 2 | *think time* | — | Random pause of 1–3 s |
| 3 | **Get current user** | `GET /auth/me` | Sends `Authorization: Bearer <accessToken>` |
| 4 | *think time* | — | Random pause of 1–3 s |
| 5 | **Refresh session** | `POST /auth/refresh` | Sends the saved `refreshToken` |

Credentials are supplied by `src/test/resources/usersData.json` using a **circular** feeder,
so users are reused in order as the load loops.

---

## Tech stack

- **Gatling** 3.15.0 (Java API) — `gatling-charts-highcharts`
- **gatling-maven-plugin** 4.21.5
- **Java** 11+
- **Maven** (the included wrapper means a local Maven install is optional)

---

## Prerequisites

- **JDK 11 or newer** installed and on your `PATH` (`java -version`)
- **Outbound internet access** to `https://dummyjson.com`
- No global Maven needed — use the bundled wrapper (`mvnw` / `mvnw.cmd`)

---

## Project structure

```
dummyJSON-API-perf-test/
├─ pom.xml                              # Maven build + Gatling plugin
├─ mvnw / mvnw.cmd                      # Maven wrapper
├─ performance-report.md               # Written-up results & analysis
├─ src/test/java/dummyJSON/Auth.java    # The simulation
├─ src/test/resources/
│  ├─ usersData.json                    # Feeder: test user credentials
│  ├─ gatling.conf                      # Gatling settings (defaults)
│  └─ logback-test.xml                  # Logging
└─ target/gatling/                      # Generated HTML reports (per run)
```

---

## How to run

All commands run from the project root.

### 1. Default run (20 users)

**Windows (PowerShell / CMD):**
```bat
mvnw.cmd gatling:test -Dgatling.simulationClass=dummyJSON.Auth
```

**macOS / Linux:**
```bash
./mvnw gatling:test -Dgatling.simulationClass=dummyJSON.Auth
```

### 2. Parameterized runs

Load is tunable at runtime via JVM system properties (passed with `-D`):

| Property | Meaning | Default |
|----------|---------|---------|
| `USERS` | Peak concurrent virtual users | `20` |
| `RAMP_DURATION` | Ramp-up time to reach `USERS`, in **seconds** | `40` |
| `TEST_DURATION` | Steady-load hold time, in **minutes** | `3` |

**Example — 40 concurrent users, 40 s ramp, 3 min hold (Windows):**
```bat
mvnw.cmd gatling:test -Dgatling.simulationClass=dummyJSON.Auth -DUSERS=40 -DRAMP_DURATION=40 -DTEST_DURATION=3
```

### 3. The two showcased runs

These are the exact runs analysed in [`performance-report.md`](performance-report.md):

```bat
:: Run A — baseline, 20 users
mvnw.cmd gatling:test -Dgatling.simulationClass=dummyJSON.Auth -DUSERS=20 -DRAMP_DURATION=40 -DTEST_DURATION=3

:: Run B — double load, 40 users
mvnw.cmd gatling:test -Dgatling.simulationClass=dummyJSON.Auth -DUSERS=40 -DRAMP_DURATION=40 -DTEST_DURATION=3
```

---

## Load model

The script uses a **closed** injection model — it holds a fixed number of *concurrent*
users (not a fixed arrival rate):

```
rampConcurrentUsers(0).to(USERS).during(RAMP_DURATION seconds)   // ramp up
constantConcurrentUsers(USERS).during(TEST_DURATION minutes)     // hold steady
```

A user finishes its Auth Flow, then a new iteration starts to keep concurrency constant,
so throughput is *emergent* from response times and the 1–3 s think-time pauses.

---

## Viewing results

After each run, Gatling prints a console summary and writes a full HTML report to:

```
target/gatling/auth-<timestamp>/index.html
```

Open `index.html` in a browser for response-time percentiles, throughput, and error breakdowns.

A written analysis comparing the 20-user and 40-user runs — average / P95, throughput,
error rate, observed facts vs. hypotheses, and environment limitations — lives in
**[`performance-report.md`](performance-report.md)**.

---

## Notes & known constraints

- **Public rate limits.** Under heavier or faster ramps the API returns **HTTP 429** and
  can hit **TLS handshake timeouts** (default 10 s) / **request timeouts** (default 60 s).
  The 20–40 user profiles above stay within tolerance; push higher and expect throttling.
- **`exitHereIfFailed`** after login prevents downstream steps from running with a missing
  token, which keeps `ACCESS_TOKEN` / `REFRESH_TOKEN` errors out of the results.
