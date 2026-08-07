# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn compile                              # Compile all modules
mvn clean verify                         # Full build + test + integration (CI gate)
mvn test -pl summer-core -am             # Test single module + its deps
mvn test -pl summer-core -am -Dtest="ClassName"  # Single test class
mvn spotless:apply                       # Format all Java code
mvn spotless:check                       # Format check (CI gate)
mvn test -pl summer-archunit             # Architecture constraint tests
mvn install -DskipTests                  # Install all jars locally, skip tests
```

- **Java 25** baseline with `--sun-misc-unsafe-memory-access=allow` (Netty/AOT need it).
- No Maven wrapper — CI uses `setup-java` which auto-installs Maven.
- `summer-parent/pom.xml` is the **mandatory build contract** (not optional). It binds Jandex indexing (`compile` phase) and AOT code generation (`process-classes` phase) — getting these wrong causes silent runtime failures, not compile errors.

## Module Topology

```
summer-framework (reactor POM) — 29 modules (framework + demos + benchmark + BOM/parents)

Framework layers (ArchitectureTest-enforced, defined by package — see ArchitectureTest.java):
  Core              summer-core                     DI, annotations, config binding, exceptions
  Infrastructure    summer-runtime, summer-engine,  DI engines, shared discovery/SPI, AOT codegen,
                    summer-runtime-web,              web route scanning, maven plugin
                    summer-aot-engine, summer-maven-plugin
  Web               summer-web (+ http/middleware/websocket), summer-boot
  Data              summer-data-jdbc, summer-data-redis
  CrossCutting      summer-aop, summer-tx
  Server            summer-web-netty, summer-grpc
  Test              summer-test, summer-tck, summer-archunit

Layer access rules: Core may not access any other layer. Infrastructure may be accessed by Web/Data/CrossCutting/Server/Test.

Banned dependencies (ArchUnit enforced): ClassGraph, CGLIB, ByteBuddy, Spring Framework, circular packages.
```

## API Surface: User-Facing vs Internal

**User API** (~60 public classes): `@Component`, `@Configuration`/`@Bean`, `@RestController`, `@Get`/`@Post`/`@Put`/`@Delete`, `@PathParam`/`@QueryParam`, `@ExceptionHandler`, `@Transactional`, `JdbcTemplate`, `@RowModel`, `SummerRedisTemplate`, `@SummerTest`, `@TestResource`, `@TestProfile`, `@DualEngine`, `@Mock`, plus 24 SPI interfaces.

**`@Internal` annotation** (SOURCE retention): ~55 framework-internal classes are marked `@Internal`. This is the **single mechanism** for marking non-public API — there is no `internal/` package anymore. SPI interfaces and user-facing classes do NOT carry `@Internal`.

Key SPI interfaces (public, no @Internal): `ApplicationRunner`, `Handler`, `Middleware`, `AuthMiddleware`, `BodyConverter`, `HttpParameterResolver`, `MethodInterceptor`, `TransactionManager`, `RowMapper<T>`, `ContainerEngine`, `TestResource`, `RouteRegistrar` (core.spi), `RouteRegistry`.

## Engine classpath constraint (2026-08-08 decision)

- **AOT engine = fat-jar engine**: wiring is compiled at build time, no index needed at runtime.
- **Runtime engine = exploded-classpath engine**: scans `META-INF/jandex.idx` at startup, so it
  belongs in dev/tests where every jar carries its own index. A shaded fat jar collapses the
  index files (same-named entries overwrite) and is **AOT-only** — the Runtime engine there
  finds only the app's own beans (loud `NoSuchBeanException`).
- Do NOT "fix" this with a shade ResourceTransformer: Jandex deliberately offers no writable
  merge API (`CompositeIndex` is an in-memory view; idx files are build-time inputs). Quarkus
  drops jandex.idx from its uber-jar for the same reason. The API's absence is the library's
  stated position.
- Engine override precedence (Spring/Quarkus convention): `-Dsummer.engine` > `SUMMER_ENGINE`
  env > `application.yml` > `DEV_ENGINE` (the dev/test default). ConfigBinder `${VAR}` placeholders
  resolve system properties before env vars (flipped 2026-08-08 to match).

## DI: Dual Engine (Runtime / AOT)

- **Runtime** (`Engine.RUNTIME`): reflection-based, Jandex index scanning at startup, ~200ms. Dev mode.
- **AOT** (`Engine.AOT`): `summer-maven-plugin` generates `$$Context` and `wire()` at compile time, ~10ms startup. Prod mode.
- Both engines share identical annotation contracts. Switch via `Engine` enum — no code changes.
- Constructor injection only. No field/setter injection. No circular dependencies. Singletons only.

## Test Infrastructure

Three tiers, from highest to lowest level:

1. **`@SummerTest`** — declarative JUnit 5 extension. Builds a whole-universe DI container, injects via constructor. Supports `@TestProfile`, `@TestResource`, `@DualEngine`, `@Mock`. **48 test classes** already use this.

2. **`TestContainer.builder()`** — programmatic builder for narrow-seed or engine-forced containers. Used internally by `SummerTestLifecycle` and by a few TCK tests that need explicit control (AOT narrow builds, negative fixture isolation). `TestContainer` is `@Internal`.

3. **`SummerTestExtension` (via `@RegisterExtension`)** — for negative tests that assert container build **failure** (circular deps, missing deps, self-injection). `SummerTestExtension` is `@Internal`.

`@TestResource` manages external resources (Postgres, Redis containers). Start/stop lifecycle with properties injected as config overrides. Currently only `RedisTestResource` exists; plan exists to create `PostgresTestResource` for demo ITs.

## Jakarta Bean Validation

- `jakarta.validation-api` 3.0.2 in BOM (`summer-dependencies`), transitively provided by `summer-web`.
- `avaje-validator` 2.17 is the runtime implementation.
- Use `ctx.validatedBody(Class<T>)` on controllers — it deserializes + auto-validates. Throws `ValidationException` (→ 400/422 via global error handler).
- DTO records use `@jakarta.validation.constraints.NotBlank`, `@Email`, etc.
- All three demos (issue-tracker, realworld, twitter) have been migrated to this pattern.

## Local Workflow Iteration (act)

- Local-only act runner image: `.github/act/Dockerfile` (header holds the build/run commands).
- **Never pass `--bind`**: copy mode keeps the container's writes in its own volume; a bind run
  writes root-owned files into the repo's `target/` dirs and breaks local builds.
- The act container's central egress is Cloudflare-blocked (it does not share the host's proxy) —
  the image bakes an aliyun mirror into its global Maven settings.
- act catches real fresh-repo CI bugs (the GitHub runner fails identically): run it before pushing
  workflow/publish changes.

## CLI decision (2026-08-08)

- **No standalone CLI in 0.x.** The command surface already exists as Maven goals:
  `mvn summer:create-app` / `mvn summer:dev` / `mvn package` (with the one-time settings.xml
  pluginGroup). A standalone CLI would wrap Maven (the build + dev are Maven-bound), adding only
  ergonomics — plus a second distribution channel (a runnable artifact + installer + versioning,
  parallel to the Maven repo).
- Revisit when: (a) tool-agnosticism arrives (Gradle support — the CLI becomes a cross-tool entry
  point), or (b) a real demand signal.

## Publish runbook (GitHub Packages)

- Tag `v*` fires `.github/workflows/publish.yml` → `mvn -B deploy -DskipTests` (all modules).
- gh token needs `read:packages` + `delete:packages` to list/clean partial deploys.
- **Known failure mode**: deploy is module-by-module; a mid-reactor deploy failure leaves a partial
  0.x set on the registry, and re-deploying the same version fails with 409 Conflict (versions are
  immutable). Recovery: delete the partial packages (`gh api -X DELETE /user/packages/maven/<name>`),
  re-tag, re-push. **Decision (2026-08-08): no deploy preflight** — the missing-distributionManagement
  class is fixed (root + summer-parent + summer-dependencies all declare it) and the recovery is
  documented + fast; a static check would guard a low-probability future regression at the cost of
  workflow complexity.

## Current Work / Pending

- **Audit complete (2026-08-07)** — the SPI refactor + two audit rounds (§11-§14, previously tracked in the deleted `CODE-AUDIT.md`) are fully resolved; durable decisions live in code comments/javadoc.
- **Do NOT commit without explicit user permission.**
- Pending: AOT codegen emits `@Bean` products by declared return type — a **package-private product class breaks the generated code** (cross-package access), and in **narrow test universes** (index = seeds only) a product class's class-level `@ConditionalOnBean` is invisible. Both surfaced via the row-mapper config; the test avoids `ReflectiveRowMapperRegistrar` (package-private) rather than fixing the generator. Needs a decision: reject package-private `@Bean` return types at codegen, or widen the narrow-index closure.
- Pending: `PostgresTestResource` for demo ITs (only `RedisTestResource` exists today).

## Conventions

- Constructor injection only. Records preferred for DTOs and config.
- Interface-based AOP (JDK dynamic proxies). No CGLIB. `this.method()` calls bypass proxy.
- `@ConfigMapping` only on interfaces.
- Virtual threads for HTTP dispatch. `HttpContext`/`Request` are NOT thread-safe.
- YAML config supports `${VAR}` and `${VAR:-default}` placeholders.
- Logging through SLF4J with `[Summer]` prefix. No `System.out`/`System.err` in framework code.
- No `module-info.java` — everything runs on classpath.
- TCK tests are in `summer-tck` (test-only, no `src/main`). Fixtures in `summer-tck-fixtures` (main-only, no `src/test`).
- Test fixtures: a whole-universe `@SummerTest` scans the running module's test-classes, so nested bean fixtures become **global** — keep them behavior-neutral (nothing may depend on their absence). Behavior-affecting fixtures (e.g. `@Replaces`) go in a dedicated fixture module (no jandex.idx) reachable only via narrow `@SummerTest(classes=...)`. `@DualEngine` tests whose universe contains a server need `server.port: 0` — each leg binds its own ephemeral port (see `@DualEngine` javadoc).
