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
summer-framework (reactor POM) — 23 framework modules + 3 demos + benchmarks

Framework layers (ArchitectureTest-enforced):
  Core              summer-core                    DI, annotations, config binding, exceptions
  Infrastructure    summer-runtime, summer-aot     Runtime/AOT DI engines
  Web               summer-web, summer-boot        HTTP abstractions, startup
  Data              summer-data-jdbc, -redis       JDBC template, Redis client
  CrossCutting      summer-aop, summer-tx          Interceptors, transactions
  Server            summer-web-netty, summer-grpc  Netty HTTP, gRPC server/client
  Test              summer-test, summer-tck, summer-archunit

Layer access rules: Core may not access any other layer. Infrastructure may be accessed by Web/Data/CrossCutting/Server/Test. Each layer's `package..` patterns are defined in ArchitectureTest.java.

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

1. **`@SummerTest`** — declarative JUnit 5 extension. Builds a whole-universe DI container, injects via constructor. Supports `@TestProfile`, `@TestResource`, `@DualEngine`, `@Mock`. **31 test classes** already use this.

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

- **CODE-AUDIT.md** in repo root: NOT git-tracked, comprehensive audit doc. Delete when refactoring is complete.
- **Do NOT commit without explicit user permission.**
- Pending: RealWorld 10 defects (R1-R10), Twitter 11 defects (T1-T11), 5 architecture violations (V1-V5). See CODE-AUDIT.md for details.
- Plan file at `.claude/plans/quirky-sauteeing-mccarthy.md`: `@TestResource` migration for demo ITs.
- Dead code eliminated: 15 items across all modules (see CODE-AUDIT.md §5).

## Conventions

- Constructor injection only. Records preferred for DTOs and config.
- Interface-based AOP (JDK dynamic proxies). No CGLIB. `this.method()` calls bypass proxy.
- `@ConfigMapping` only on interfaces.
- Virtual threads for HTTP dispatch. `HttpContext`/`Request` are NOT thread-safe.
- YAML config supports `${VAR}` and `${VAR:-default}` placeholders.
- Logging through SLF4J with `[Summer]` prefix. No `System.out`/`System.err` in framework code.
- No `module-info.java` — everything runs on classpath.
- TCK tests are in `summer-tck` (test-only, no `src/main`). Fixtures in `summer-tck-fixtures` (main-only, no `src/test`).
