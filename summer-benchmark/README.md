# Summer Benchmark Suite

This module contains the strict, containerized load testing suite for the Summer framework, comparing its raw HTTP parsing, routing, and serialization performance against the industry standard: **Spring Boot (Tomcat)**.

## Project Structure

- `benchmark-common`: Shared POJOs (`User`) and mock services (`UserService` using `ConcurrentHashMap`).
- `benchmark-spring-boot`: Spring Boot 3 baseline application.
- `benchmark-summer`: Summer framework application running on Netty.
- `k6-scripts`: Load testing scripts for use with Grafana k6.
- `docker-compose.yml`: Defines the isolated, resource-constrained container environments.
- `run-benchmarks.py`: The Python orchestrator script that automatically builds, runs, tests, and cleans up the benchmark environments.

## Architecture & Constraints

To ensure an absolutely fair and production-like comparison, this benchmark suite runs completely isolated inside Docker containers rather than on bare metal.

**Strict Constraints applied via `docker-compose`:**
1. **Identical Base Image**: Both frameworks use `eclipse-temurin:25-jre-alpine` (a highly stripped-down Linux environment using `musl` libc).
2. **Resource Limits**: Both containers are strictly hard-capped to **2 CPUs** and **512MB RAM**. This deliberately simulates a typical constrained Kubernetes Pod, testing how each framework handles garbage collection pressure and thread contention under limits.
3. **Variable Control**:
    - Both use **Virtual Threads** (Java 25).
    - Both use **Jackson** for JSON serialization.
    - No external I/O (Database/Redis) is used; state is stored in a `ConcurrentHashMap` to strictly measure *framework overhead* rather than database speed.

## Running the Benchmark

### 1. Requirements
- Java 25 (installed on the host for compilation)
- Maven
- Docker & Docker Compose
- Python 3

### 2. Execution

Simply execute the orchestrator script from the `summer-benchmark` root directory:

```bash
python run-benchmarks.py
```

**What the script does:**
1. Compiles all Maven modules natively on your host machine.
2. Uses Docker Compose to bring up the Spring Boot target and a fresh Grafana k6 instance.
3. Runs the load test (warmup + 10s benchmark), exports the summary, and gracefully destroys the containers.
4. Waits 3 seconds to let Docker networks settle.
5. Repeats the exact same process from scratch for Summer.
6. Parses the resulting JSON files and generates a beautiful markdown comparison report (`benchmark-results.md`).
