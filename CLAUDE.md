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

Key SPI interfaces (public, no @Internal): `ApplicationRunner`, `Provider<T>`, `Handler`, `Middleware`, `AuthMiddleware`, `BodyConverter`, `HttpParameterResolver`, `MethodInterceptor`, `TransactionManager`, `RowMapper<T>`, `ContainerEngine`, `TestResource`.

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

## Current Work / Pending

- **CODE-AUDIT.md** in repo root: git-tracked, comprehensive audit doc. §10 = items resolved by the 2026-08 SPI refactor; §11 = re-verification table of every remaining item (verdicts: resolved / fixed / reduced / live). Delete when §11 has no live items left.
- **Do NOT commit without explicit user permission.**
- Pending (see CODE-AUDIT.md §11): engine-level dedup (6.4 `@WithDefault`×3, 6.7 `@Mock`×2, 6.10 `List<T>`×4), design decisions (8.5 `MetricsRegistry` annotation style, 8.8 `BeanEnrichment` visibility), god-class split (9.4 `WireMethodGenerator`).
- Pending: `PostgresTestResource` for demo ITs (only `RedisTestResource` exists today).
- Dead code eliminated: §5 items verified in CODE-AUDIT.md §11 — most auto-resolved by the refactor (5.1/5.2/5.3/5.6/5.7), 5.8 dead overloads removed, 5.9-5.11 fixed.

## Conventions

- Constructor injection only. Records preferred for DTOs and config.
- Interface-based AOP (JDK dynamic proxies). No CGLIB. `this.method()` calls bypass proxy.
- `@ConfigMapping` only on interfaces.
- Virtual threads for HTTP dispatch. `HttpContext`/`Request` are NOT thread-safe.
- YAML config supports `${VAR}` and `${VAR:-default}` placeholders.
- Logging through SLF4J with `[Summer]` prefix. No `System.out`/`System.err` in framework code.
- No `module-info.java` — everything runs on classpath.
- TCK tests are in `summer-tck` (test-only, no `src/main`). Fixtures in `summer-tck-fixtures` (main-only, no `src/test`).
