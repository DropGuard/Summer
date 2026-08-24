# Cross-Language Framework Benchmark Results

| Metric | Spring Boot (Java / Jackson) | Summer (Java / Jackson) | Summer (Java / Avaje) | Gin (Go / Stdlib) | Fastify (Node.js / V8) |
|---|---|---|---|---|---|
| Requests/sec (RPS) | 33640.66 | 52242.69 | 52465.47 | 40670.69 | 38069.28 |
| Total Requests | 336944 | 522824 | 524840 | 406852 | 381116 |
| Avg Latency (ms) | 2.90 | 1.86 | 1.85 | 2.39 | 2.55 |
| P50 Latency (ms) | 2.33 | 1.31 | 1.30 | 1.59 | 1.93 |
| P95 Latency (ms) | 6.45 | 5.34 | 5.25 | 7.12 | 6.67 |
| P99 Latency (ms) | 10.61 | 9.40 | 9.59 | 11.82 | 11.39 |