# PROJECT KNOWLEDGE BASE

**Generated:** 2026-07-14
**Stack:** Java 25+ · Maven multi-module · Netty · Jandex · JDK Dynamic Proxies · JUnit 5
**Description:** Minimalist JDK-native CRUD framework. Clarity-first reconstruction of Spring-like runtime — singleton DI, annotation routing, virtual threads.

## STRUCTURE

```
summer-framework/
├── summer-core/           # DI container, annotations, config binding, exceptions
├── summer-engine/         # Shared discovery pipeline + ContainerEngine SPI (Jandex types)
├── summer-web/            # Router, Middleware, Handler, WS abstractions
│   ├── summer-web-http/   #   HTTP types (thin)
│   ├── summer-web-netty/  #   Netty server impl
│   ├── summer-web-middleware/  # CORS, Logging, Metrics
│   └── summer-web-websocket/   # Radix/Map WS routers
├── summer-runtime/        # Runtime DI engine (reflection; pure DI, zero web refs)
├── summer-runtime-web/    # Web bridge: route scanning SPI impl, handler factory
├── summer-aot-engine/     # AOT code-gen DI engine
├── summer-boot/           # SummerApplication entry point
├── summer-aop/            # JDK proxy interceptor chain
├── summer-tx/             # Transaction mgmt (REQUIRED only)
├── summer-data-jdbc/      # JDBC template + RowModel + JDBC tx context
├── summer-data-redis/     # Redis client
├── summer-grpc/           # gRPC client + server
├── summer-maven-plugin/   # AOT codegen + Jandex indexing
├── summer-test/           # @SummerTest, @Mock test infra
├── summer-tck/            # Behavioral tests (Runtime + AOT engines)
├── summer-tck-fixtures/   # Shared test fixtures
├── summer-archunit/       # Architecture constraint tests
├── summer-realworld/      # RealWorld clone (hurl e2e)
├── summer-issue-tracker/  # Issue tracker demo (PG)
├── summer-twitter/        # Twitter clone (PG + Redis)
└── summer-benchmark/      # k6 load tests (Summer vs Spring Boot)
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| App entry point | `summer-boot/.../boot/SummerApplication.java` |
| DI engine selection | `summer-engine/.../engine/DiEngine.java` |
| ContainerEngine SPI impls | `summer-runtime/.../runtime/RuntimeContainer.java`, `summer-aot-engine/.../aot/AotContainer.java` (via `META-INF/services`) |
| Runtime DI (reflection) | `summer-runtime/.../runtime/RuntimeContainer.java` + `BeanInstantiator` + `JandexIndexLoader` |
| AOT DI (code-gen) | `summer-aot-engine/.../aot/AotEngine.java` |
| Shared discovery pipeline | `summer-engine/.../engine/Discovery.java` + `BeanEnrichment` + `SharedConditionEvaluator` |
| HTTP server (Netty) | `summer-web-netty/.../server/NettyServerRunner.java` |
| Router builder | `summer-web/.../web/HttpRouter.java` + Builder |
| Web route scanning (shared SPI) | `summer-runtime-web/.../runtime/web/WebRouteScanner.java` |
| AOP processor | `summer-runtime/.../runtime/RuntimeAopProcessor.java` |
| Test container builder | `summer-test/.../test/TestContainer.java` |
| Dual-engine TCK tests | `summer-tck/src/test/java/com/github/dropguard/summer/tck/` |
| Architecture rules | `summer-archunit/src/test/java/com/github/dropguard/summer/arch/` |
| Showcase app | `summer-twitter/src/main/java/com/github/dropguard/summer/twitter/` |
| RealWorld app | `summer-realworld/src/main/java/com/github/dropguard/summer/realworld/` |

## CONVENTIONS

- **Constructor injection only** — no field/setter injection. Fail-fast on ambiguity.
- **Interface-based AOP** — JDK dynamic proxy only. No CGLIB. Internal `this.method()` calls bypass proxy.
- **Records for config/data** — `@ConfigMapping` interfaces and `@RowModel` records for typed config/data.
- **Dual DI engine** — RUNTIME (reflection, dev) or AOT (compile-time wire(), prod). Switched via `-Dsummer.engine`. Both engines share the `Discovery`/`BeanEnrichment`/`SharedConditionEvaluator` pipeline in `summer-engine`.
- **Virtual threads** — HTTP dispatch on `Thread.startVirtualThread`. `HttpContext`/`Request` not thread-safe.
- **Singletons only** — no prototype scope. Use `Provider<T>` for manual creation.
- **Explicit middleware** — global middleware registered via `SummerApplication.apply()`. Route-level via `Router.Builder.mount()`.
- **REQUIRED-only transactions** — no distributed/XA.
- **YAML config** — `application.yml` bound to `@ConfigMapping` interfaces. Nested under server/data/ keys. Supports `${VAR}` and `${VAR:-default}` placeholders (resolved from env var, then system property, then default) for externalized config.
- **Logging via SLF4J** — diagnostics go through the logging facade (SLF4J), never straight to the console. The deployer owns the logging backend/aggregation (Logback/Log4j/Loki/cloud) — same boundary as health probes and graceful shutdown. Framework bootstrap/DI/AOT stages log with the `[Summer]` prefix.
- **Tests** — JUnit 5 + Mockito. `@SummerTest` builds a whole-universe container (narrow seeding via `TestContainer.buildForTest(Class)`); `@DualEngine` runs both engines, `@TestProfile`/`@TestResource`/`@Mock` adjust the universe. `@Mock` injects Mockito mocks. `*IT.java` for integration (Failsafe).

## ANTI-PATTERNS

- ~~Reflection outside `summer-runtime`~~ (ArchUnit enforced)
- ~~CGLIB/ByteBuddy/ClassGraph deps~~ (ArchUnit enforced)
- ~~ConcurrentHashMap or ServiceLoader in production~~ (ArchUnit enforced)
- ~~CJK characters in comments~~ (ArchUnit enforced)
- ~~Field/setter injection~~ (design constraint)
- ~~Catch `Throwable`~~ — catch specific types. Broad `catch (Exception e)` is tolerated but discouraged.
- ~~Empty catch blocks~~ — at minimum log the exception.
- ~~`System.out`/`System.err`/`Throwable.printStackTrace()` in framework code~~ (ArchUnit enforced, `LoggingConventionTest`) — route through SLF4J. Sole exception: `SummerApplication`'s startup banner (class-scoped carve-out). Demos/fixtures are out of scope and may print.
- ~~`@SuppressWarnings("unchecked")`~~ — justify in comment (23 occurrences as of 2026-08-06).
- ~~Returning null from methods~~ — prefer `Optional` or throw.
- ~~`Thread.sleep()` for synchronization~~ — use proper coordination.

## COMMANDS

```bash
mvn compile                   # Compile all modules
mvn clean verify              # Full build + test + integration (CI)
mvn test -pl summer-core -am  # Test single module
mvn test -pl summer-core -am -Dtest="ClassName"  # Single test class
mvn spotless:apply            # Format all Java code
mvn spotless:check            # Format check (CI gate)
mvn test -pl summer-archunit  # Architecture tests
mvn compile exec:java -pl summer-twitter -am  # Run showcase app
```

## NOTES

- JDK 25 baseline (`--sun-misc-unsafe-memory-access=allow` for Netty/AOT).
- No Maven wrapper — CI uses `setup-java` which auto-installs Maven.
- No `module-info.java` yet — all runs on classpath.
- Shared exceptions live in `summer-core/.../core/exception/` (no separate exceptions module).
- `summer-tck` is test-only (no `src/main`). `summer-tck-fixtures` is main-only (no `src/test`).

## CONTAINERIZATION

How to ship a Summer app as a container image. The framework owns runtime
semantics (health probes, graceful shutdown); the image is the deployer's
choice — Summer ships no Dockerfile, by design.

- **Multi-stage build** — JDK only in the build stage; runtime image is
  JRE-only (`eclipse-temurin:26-jre` matches the benchmark baseline).
- **`ENTRYPOINT` must be exec-form** (`ENTRYPOINT ["java","-jar","app.jar"]`).
  A shell-form `ENTRYPOINT` makes a shell PID 1 that swallows `SIGTERM`, so
  `BeanContainer.close()` never runs and graceful shutdown silently dies.
- **Health probes** — `/health/ready` (readiness, flips to 503 during
  shutdown) and `/health/live` (liveness). Point the orchestrator's readiness
  probe at `/health/ready` so it stops routing before the server stops
  accepting.
- **Graceful shutdown budget** — `ShutdownConfig.timeoutMs` (default 10s,
  bounds the in-flight drain) must sit *inside* the platform's kill budget:
  bare `docker stop` defaults to 10s (raise with `--stop-timeout`), and on
  Kubernetes set `terminationGracePeriodSeconds` strictly greater than
  `timeoutMs` + other teardown work.
- **Externalized config** — inject the datasource URL etc. via `${VAR}` /
  `${VAR:-default}` in `application.yml` (resolved from env at bind time), so
  the same image runs against any Postgres service name without a rebuild.

Note: the demo `summer-issue-tracker` deliberately keeps its
`application.yml` datasource URL as `jdbc:postgresql://localhost:5432/...`
overridable via `SUMMER_DB_URL`. A real deployment points `SUMMER_DB_URL` at
the DB service name.

