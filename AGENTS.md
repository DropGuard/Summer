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
├── summer-tck-invisible-fixtures/  # Whole-universe-invisible narrow fixtures (no jandex.idx)
├── summer-archunit/       # Architecture constraint tests
├── samples/               # Demo applications aggregator
│   ├── summer-realworld/      # RealWorld clone (hurl e2e)
│   ├── summer-issue-tracker/  # Issue tracker demo (PG)
│   └── summer-twitter/        # Twitter clone (PG + Redis)
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
| Showcase app | `samples/summer-twitter/src/main/java/com/github/dropguard/summer/twitter/` |
| RealWorld app | `samples/summer-realworld/src/main/java/com/github/dropguard/summer/realworld/` |

## LAYERS & ACCESS RULES

Layer access is ArchUnit-enforced by package (see `ArchitectureTest`):

```
Core              summer-core                     DI, annotations, config binding, exceptions
Infrastructure    summer-runtime, summer-engine,  DI engines, shared discovery/SPI, AOT codegen,
                  summer-runtime-web,              web route scanning, maven plugin
                  summer-aot-engine, summer-maven-plugin
Web               summer-web (+ http/middleware/websocket), summer-boot
Data              summer-data-jdbc, summer-data-redis
CrossCutting      summer-aop, summer-tx
Server            summer-web-netty, summer-grpc
Test              summer-test, summer-tck, summer-archunit
```

- Core may not access any other layer. Infrastructure may be accessed by Web/Data/CrossCutting/Server/Test.
- Banned dependencies (ArchUnit enforced): ClassGraph, CGLIB, ByteBuddy, Spring Framework, circular packages.

## CONVENTIONS

- **Constructor injection only** — no field/setter injection. Fail-fast on ambiguity.
- **Interface-based AOP** — JDK dynamic proxy only. No CGLIB. Internal `this.method()` calls bypass proxy.
- **AOP lookup contract ("one bean, one form")** — an AOP-bound bean exists in the lookup plane
  ONLY as its proxy (under its unique interface keys AND as the value of its concrete-class key;
  the proxy is not assignable to the concrete class, so `getBean(ConcreteClass)` fails loudly with
  `NoSuchBeanException` — declare dependencies on interfaces). Collection injection is homogeneous:
  one entry per bean, always the proxy. Lifecycle never routes through lookup (`@PostConstruct`
  pre-wrap on the raw target; close/seal forward through the proxy).
- **Records for config/data** — `@ConfigMapping` interfaces and `@RowModel` records for typed config/data.
- **Dual DI engine** — RUNTIME (reflection, dev) or AOT (compile-time wire(), prod). Switched via `-Dsummer.engine`. Both engines share the `Discovery`/`BeanEnrichment`/`SharedConditionEvaluator` pipeline in `summer-engine`.
- **Virtual threads** — HTTP dispatch on `Thread.startVirtualThread`. `HttpContext`/`Request` not thread-safe.
- **Singletons only** — no prototype scope. Use `Provider<T>` for manual creation.
- **Explicit middleware** — global middleware registered via `SummerApplication.apply()`. Route-level via `Router.Builder.mount()`.
- **REQUIRED-only transactions** — no distributed/XA.
- **YAML config** — `application.yml` bound to `@ConfigMapping` interfaces. Nested under server/data/ keys. Supports `${VAR}` and `${VAR:-default}` placeholders (resolved from system property, then env var, then default — the Spring/Quarkus convention, flipped 2026-08-08) for externalized config.
- **Logging via SLF4J** — diagnostics go through the logging facade (SLF4J), never straight to the console. The deployer owns the logging backend/aggregation (Logback/Log4j/Loki/cloud) — same boundary as health probes and graceful shutdown. Framework bootstrap/DI/AOT stages log with the `[Summer]` prefix.
- **Tests** — JUnit 5 + Mockito. `@SummerTest` builds a whole-universe container (narrow seeding via `TestContainer.buildForTest(Class)`); `@DualEngine` runs both engines, `@TestProfile`/`@TestResource`/`@Mock` adjust the universe. `@Mock` injects Mockito mocks. `@TestResource` is the Quarkus lifecycle (initArgs via `init`, field injection via `inject`, `order()`); its overrides are dotted-YAML-path keys (env-style keys silently fall back to `@WithDefault` — a pinned contract in `TestResourceContractTest`). `*IT.java` runs under the Failsafe, bound in summer-parent's active plugins — the CI fails if the IT-bearing modules run 0 tests (the old bare declaration silently skipped every IT). Whole-universe-invisible fixtures (the narrow-seeded sad-path beans AND the narrow-only positive configs, e.g. the row-model metadata regression) live in `summer-tck-invisible-fixtures` — no jandex-maven-plugin, so the jar carries the .class bytes (the narrow `@SummerTest` seeds them by name) but no jandex.idx; the boundary is the archive's absence from the indexed path, not an exclude list (the Quarkus Arc model).

## ANTI-PATTERNS

- ~~Reflection outside `summer-runtime`~~ (ArchUnit enforced)
- ~~CGLIB/ByteBuddy/ClassGraph deps~~ (ArchUnit enforced)
- ~~ConcurrentHashMap or ServiceLoader in production~~ (ArchUnit enforced)
- ~~CJK characters in comments~~ (ArchUnit enforced)
- ~~Field/setter injection~~ (design constraint)
- ~~Catch `Throwable`~~ — catch specific types. Broad `catch (Exception e)` is tolerated but discouraged.
- ~~Empty catch blocks~~ — at minimum log the exception.
- ~~`System.out`/`System.err`/`Throwable.printStackTrace()` in framework code~~ (ArchUnit enforced, `LoggingConventionTest`) — route through SLF4J. Sole exception: `SummerApplication`'s startup banner (class-scoped carve-out). Demos/fixtures are out of scope and may print.
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
mvn compile exec:java -f samples/summer-twitter/pom.xml  # Run showcase app
mvn install -DskipTests       # Install all jars locally, skip tests
```

## NOTES

- **Stale artifacts & report reading (build-credibility rules)** — javac never deletes from
  `target/classes` and surefire keeps previous reports when compilation fails, so: after changing
  a public signature (constructor/method param types), build that module chain with `mvn clean`;
  and never read a surefire report without the matching `BUILD SUCCESS/FAILURE` line — a stale
  "all green" report next to a failed build is the classic false signal. The AOT mojo wipes and
  regenerates its sources every run; stale compiled outputs are reconciled by
  {@code SummerSourceIndex} against the live source set, with a snapshot at
  `target/summer/source-classes.tsv`. `mvn clean` remains the sledgehammer.
- JDK 25 baseline (`--sun-misc-unsafe-memory-access=allow` for Netty/AOT).
- No Maven wrapper — CI uses `setup-java` which auto-installs Maven.
- `summer-parent/pom.xml` is the **mandatory build contract** (not optional): it binds Jandex
  indexing (`compile`) and AOT generation (`process-classes`). Getting these wrong causes silent
  runtime failures, not compile errors.
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

## API SURFACE: USER-FACING VS INTERNAL

**User API** (~60 public classes): `@Component`, `@Configuration`/`@Bean`, `@RestController`, `@Get`/`@Post`/`@Put`/`@Delete`, `@PathParam`/`@QueryParam`, `@ExceptionHandler`, `@Transactional`, `JdbcTemplate`, `@RowModel`, `SummerRedisTemplate`, `@SummerTest`, `@TestResource`, `@TestProfile`, `@DualEngine`, `@Mock`, plus 24 SPI interfaces.

**`@Internal` annotation** (SOURCE retention): ~55 framework-internal classes are marked `@Internal`. This is the **single mechanism** for marking non-public API — there is no `internal/` package anymore. SPI interfaces and user-facing classes do NOT carry `@Internal`.

Key SPI interfaces (public, no @Internal): `ApplicationRunner`, `Handler`, `Middleware`, `AuthMiddleware`, `BodyConverter`, `HttpParameterResolver`, `MethodInterceptor`, `TransactionManager`, `RowMapper<T>`, `ContainerEngine`, `TestResource`, `RouteRegistrar` (core.spi), `RouteRegistry`.

## DUAL DI ENGINE

- **Runtime** (`Engine.RUNTIME`): reflection-based, Jandex index scanning at startup, ~200ms. Dev mode.
- **AOT** (`Engine.AOT`): `summer-maven-plugin` generates `$$Context` and `wire()` at compile time, ~10ms startup. Prod mode.
- Both engines share identical annotation contracts. Switch via `Engine` enum — no code changes. Constructor injection only. No field/setter injection. No circular dependencies. Singletons only.
- **Engine classpath constraint**: the AOT engine is wired at build time and needs no index at runtime — it is the fat-jar engine. The Runtime engine scans `META-INF/jandex.idx` at startup, so it belongs on the exploded classpath (dev mode, tests) where every jar carries its own index; a shaded fat jar collapses the index files and is AOT-only (a Runtime boot there finds only the app's own beans — loud `NoSuchBeanException`). Do NOT "fix" this with a shade ResourceTransformer: Jandex deliberately offers no writable merge API (`CompositeIndex` is an in-memory view; idx files are build-time inputs); Quarkus drops jandex.idx from its uber-jar for the same reason.
- **Engine override precedence** (Spring/Quarkus convention): `-Dsummer.engine` system property > `SUMMER_ENGINE` env var > `application.yml` > the `DEV_ENGINE` default (the dev/test engine — named for its role, not "default", so it cannot be misread as a production default).

## TEST INFRASTRUCTURE

Three tiers, from highest to lowest level:

1. **`@SummerTest`** — declarative JUnit 5 extension. Builds a whole-universe DI container, injects via constructor. Supports `@TestProfile`, `@TestResource`, `@DualEngine`, `@Mock`. 48 test classes use this.
2. **`TestContainer.builder()`** — programmatic builder for narrow-seed or engine-forced containers. Used internally by `SummerTestLifecycle` and by a few TCK tests needing explicit control (AOT narrow builds, invisible-fixture isolation). `TestContainer` is `@Internal`.
3. **`SummerTestExtension` (via `@RegisterExtension`)** — for negative tests that assert container build **failure** (circular deps, missing deps, self-injection). `SummerTestExtension` is `@Internal`.

`@TestResource` manages external resources (Postgres, Redis containers): `RedisTestResource`
(summer-data-redis) + `PostgresTestResource` (summer-data-jdbc, test scope — returns the
`datasource.*` overrides a `@ConfigMapping(prefix = "datasource")` binds).

## JAKARTA BEAN VALIDATION

- `jakarta.validation-api` 3.0.2 in BOM (`summer-dependencies`), transitively provided by `summer-web`.
- `avaje-validator` 2.17 is the runtime implementation.
- Use `ctx.validatedBody(Class<T>)` on controllers — it deserializes + auto-validates. Throws `ValidationException` (→ 400/422 via global error handler).
- DTO records use `@jakarta.validation.constraints.NotBlank`, `@Email`, etc.
- All three demos (issue-tracker, realworld, twitter) are migrated to this pattern.


## RELEASE PROCESS

Summer uses automated tag-driven CI/CD deployment to Maven Central via GitHub Actions (`publish.yml`).
During development, the version on `main` is always a `-SNAPSHOT` (e.g., `0.3.2-SNAPSHOT`).

To release a new version, use the automated release script:
```bash
# Preview the release without modifying any files or git state:
./scripts/release.sh --dry-run <version>

# Execute the release (updates all POMs, runs spotless, commits, tags, bumps dev snapshot, and pushes):
./scripts/release.sh <version> [next-snapshot-version]

# Example:
./scripts/release.sh 0.3.3 0.3.4-SNAPSHOT
```
Pushing the `v*` tag automatically triggers the GitHub Actions `publish.yml` workflow, which handles Java 25 compilation, GPG signing, Maven Central staging/publishing, and GitHub Release notes generation.
