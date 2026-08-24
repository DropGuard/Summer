# Summer Benchmark Suite

This module contains the reproducible, cross-language containerized load testing suite comparing Summer against industry-standard web frameworks across runtimes:
- **Spring Boot 3 (Java / Tomcat Virtual Threads)**
- **Summer (Java / Netty + Jackson)**
- **Summer (Java / Netty + Avaje-JSONB)**
- **Gin (Go / net/http goroutines)**
- **Fastify (Node.js / V8 cluster workers)**

## Strict Methodology & Constraints

To ensure an accurate, fair, and production-representative comparison, the benchmarks run under four strict constraints:

1. **CPU Core Pinning (Physical Core Isolation)**:
   - Target services run with `cpuset: "0,1"` (pinned to physical cores 0 and 1).
   - The Grafana k6 runner runs isolated with `cpuset: "2,3"` (pinned to physical cores 2 and 3).
   - This ensures the load generator and the target server never steal CPU cycles or trigger cross-core cache thrashing.
2. **Containerized Resource Limits**:
   - Every service is strictly hard-capped to **2 CPUs** and **512MB RAM**, accurately simulating a constrained Kubernetes Pod.
3. **Realistic 4-Step CRUD Lifecycle**:
   - Not a synthetic "Hello World" benchmark. Every iteration executes a complete user lifecycle:
     `POST /users` (Create) $\to$ `GET /users/{id}` (Read) $\to$ `PUT /users/{id}` (Update) $\to$ `DELETE /users/{id}` (Delete).
   - Exercises HTTP parsing, routing trees, JSON serialization/deserialization, concurrent Map mutation, and virtual thread scheduling.
4. **Zero Client GC Distortion (`discardResponseBodies: true`)**:
   - k6 is configured with `discardResponseBodies: true` to discard response payloads immediately upon HTTP 200 verification.
   - Prevents k6's internal JavaScript heap from accumulating millions of string allocations at 26k+ RPS, eliminating client-side GC pauses and ensuring latency measurements (P50/P95/P99) purely reflect server-side performance.

## Latest Benchmark Results (2 CPU / 512MB RAM)

| Metric | Spring Boot (Java / Jackson) | Summer (Java / Jackson) | Summer (Java / Avaje) | Gin (Go / Stdlib) | Fastify (Node.js / V8) |
|---|---|---|---|---|---|
| **Requests/sec (RPS)** | 33,640.66 | **52,242.69** | **52,465.47** | 40,670.69 | 38,069.28 |
| **Total Requests (10s)** | 336,944 | **522,824** | **524,840** | 406,852 | 381,116 |
| **Avg Latency (ms)** | 2.90 | **1.86** | **1.85** | 2.39 | 2.55 |
| **P50 Latency (ms)** | 2.33 | **1.31** | **1.30** | 1.59 | 1.93 |
| **P95 Latency (ms)** | 6.45 | **5.34** | **5.25** | 7.12 | 6.67 |
| **P99 Latency (ms)** | 10.61 | **9.40** | **9.59** | 11.82 | 11.39 |

## Running the Benchmark

Execute the automated runner script:

```bash
./run_all_benchmarks.sh
```

**What the script does:**
1. Compiles the latest Summer benchmark JARs natively via Maven (`mvn clean package`).
2. Iterates through all 5 framework profiles using Docker Compose (20s warmup + 10s benchmark with CPU core pinning).
3. Parses resulting JSON summaries and automatically generates/updates `benchmark-results.md`.
