# CLAUDE.md

## Project Overview

Summer Framework — a minimalist JDK-native framework for building CRUD APIs. Philosophy: explicit execution over implicit magic. Runtime behavior is modeled as visible chains (`HttpServer → MiddlewareChain → Router → Handler`; `Proxy → InterceptorChain → Target Method`).

## Build & Test Commands

Uses Maven (via `mvnd` Maven Daemon). JDK 26 required. The Makefile wraps all commands and sets `MAVEN_OPTS=--sun-misc-unsafe-memory-access=allow`.

```bash
make compile          # Build all modules
make test             # Run unit tests (mvnd clean test)
make install          # Install to local repo (skip tests)
make run              # Run summer-example app
make realworld        # Run RealWorld example app
make arch             # Run ArchUnit architecture tests (mvnd test -pl summer-archunit)
make fmt              # Format code (mvnd spotless:apply)
make check            # Check formatting (mvnd spotless:check)
make pre-commit       # fmt + check + test in sequence
make benchmark        # Run benchmarks (Python script)
```

Run a single test:
```bash
mvnd test -pl summer-core -Dtest=SomeTestClass
mvnd test -pl summer-core -Dtest=SomeTestClass#methodName
```

Override Maven binary: `MVN=mvn make compile` (if `mvnd` is not installed).

## Module Dependency Graph

```
summer-core (foundation — no Summer dependencies)
  +-- summer-aop (AOP interfaces — no Summer dependencies)
  |     +-- summer-tx (also summer-core; @Transactional, HikariCP)
  |     +-- summer-maven-plugin (also summer-core; AOT code gen via Jandex)
  +-- summer-web (HTTP/WebSocket routing abstractions)
  |     +-- summer-web-http (annotation routing implementation)
  |     +-- summer-web-middleware (built-in middleware: metrics, error handling)
  |     +-- summer-web-websocket (WebSocket support)
  |     +-- summer-web-netty (also summer-web-http, summer-web-websocket; Netty server)
  |     +-- summer-runtime (also summer-core, summer-aop; runtime DI engine)
  |           +-- summer-boot (also summer-aop; bootstrap/startup)
  +-- summer-data-jdbc (also summer-tx; zero-reflection JDBC)
  +-- summer-data-redis (Lettuce-based Redis)
  +-- summer-grpc (gRPC integration)
```

Test infrastructure: `summer-test` (utilities), `summer-tck-fixtures` (fixture beans), `summer-tck` (unified TCK for both engines).
Architecture enforcement: `summer-archunit` (ArchUnit rules).
Examples: `summer-example`, `summer-realworld`. Benchmarks: `summer-benchmark/` (separate parent, Java 21).

## Architecture

### Two DI Engines

The `DiEngine` interface (`summer-core`) abstracts startup. Two implementations:
- **Runtime** (`summer-runtime`): Jandex-based classpath scanning, dependency graph with topological sort, JDK dynamic proxy creation. Used when `Engine.RUNTIME` is selected.
- **AOT** (`summer-maven-plugin`): Compile-time code generation via Jandex index + JavaPoet. Generates AotContext, AOP proxies, route adapters, RowMappers. Default in example apps.

`SummerApplication.run()` in `summer-boot` orchestrates: banner → create ApplicationContext → resolve interceptor mode → run `ApplicationRunner` beans → register shutdown hook.

### Component Model

`@Component` is the root marker annotation. Meta-annotations that carry `@Component`:
- `@Configuration` — config classes with `@Bean` factory methods
- `@RestController` — HTTP controllers (auto-discovered, routes registered via `RouteRegistrar` SPI)
- `@GlobalMiddleware` — cross-cutting web middleware

Key annotations: `@ConditionalOnBean` (conditional registration), `@Replaces` (test doubles), `@ConfigurationProperties` (YAML binding to records).

**Rules**: Exactly one public constructor per `@Component`. Constructor injection only — no field/setter injection. All beans are singletons. Circular dependencies are rejected at startup.

### AOP System

Binding-annotation pattern (CDI-style):
1. Define a custom annotation meta-annotated with `@InterceptorBinding` (e.g., `@Transactional`)
2. Implement `MethodInterceptor` and annotate the interceptor class with that binding annotation
3. The runtime matches interceptors to methods carrying the same binding annotation

AOP is applied at the interface level: `getBean(InterfaceType.class)` returns a proxy; `getBean(ConcreteType.class)` bypasses it. Internal `this.method()` calls skip the proxy (JDK dynamic proxy limitation).

### Web Layer

Middleware model inspired by Gin/Express:
- `Middleware` is `Handler apply(Handler handler)` — wraps handlers for cross-cutting concerns
- `HttpRouterBuilder` provides fluent DSL: `get()`, `post()`, `use()`, `group()`, `mount()`
- Controllers use `@RestController("/path")`, `@Get`, `@Post`, `@Put`, `@Delete`, `@PathParam`, `@QueryParam`
- `WebContext` is request-scoped, NOT thread-safe — never pass to background threads

### Execution Chain

```
HttpServer → MiddlewareChain → Router → Handler
Proxy → InterceptorChain → Target Method
```

## Architecture Rules (Enforced by ArchUnit)

Run `make arch` to verify. Key rules enforced in `summer-archunit`:

1. **Layered architecture** — dependencies flow downward only:
   - Core: `summer.core` — may not access any other layer
   - CrossCutting: `summer.aop`, `summer.tx`, `summer.validation`
   - Infrastructure: `summer.runtime`, `summer.plugin`
   - Web: `summer.web`, `summer.boot`
   - Data: `summer.data`
   - Server: `summer.web.netty`, `summer.grpc`
   - Test: `summer.test`, `summer.tck`, `summer.arch`
2. **No circular dependencies** between `summer.*` packages
3. **No CGLIB, ByteBuddy, or ClassGraph** dependencies anywhere
4. **Reflection confined to `summer-runtime`** — `java.lang.reflect.*`, `java.lang.invoke.*`, and reflective `Class` methods (`forName`, `newInstance`, `getDeclaredMethods`, etc.) are banned outside `summer.runtime` and `summer.test`
5. **Comments must be ASCII** — no CJK characters in Java source comments

## Design Constraints (Intentional)

These are non-goals. Do not add:
- Field injection, setter injection, circular dependency resolution
- CGLIB/subclass-based proxying (JDK dynamic proxy only)
- Prototype scope (singleton only; use `Provider<T>` for manual creation)
- Auto-configuration / classpath guessing
- Bean post-processors, complex lifecycle hooks
- Distributed transactions
- Built-in thread pools (use virtual threads or bring your own)

## Testing Pattern (TCK)

The `summer-tck` module uses abstract test classes (`AbstractDependencyInjectionTCK`, `AbstractAopTCK`, `AbstractWebRouteTCK`, `AbstractTransactionTCK`) that define behavioral contracts. Each declares `protected abstract ApplicationContext createAndInitializeContext()`. Concrete implementations (e.g., `RuntimeDiTest`, `RuntimeAopTest`) supply the engine-specific factory. This ensures both AOT and runtime engines satisfy the same contract.

## Key Conventions

- Java 26 baseline, virtual threads for HTTP request handling
- Jandex (`META-INF/jandex.idx`) for bytecode indexing — framework modules ship pre-built indexes
- `ApplicationContext` itself is injectable into any constructor
- `AutoCloseable` beans are tracked and closed in reverse instantiation order
- Package naming: each module uses `summer.<module-name>` as root package (e.g., `summer.core`, `summer.web`, `summer.aop`)
- Standard Maven layout: `src/main/java`, `src/test/java`
- CI runs on GitHub Actions: `mvn spotless:check` then `mvn clean verify` with `CI=true` (enables integration test profiles)

<!-- gitnexus:start -->
# GitNexus

项目已索引为 **Summer**。索引过期时运行 `npx gitnexus analyze`。

探索用 `gitnexus_query`，大改动前用 `gitnexus_impact` 查影响范围，重命名用 `gitnexus_rename`。

## Build

- AOT 插件在 `execute()` 开头清空 `target/generated-sources/aot`，强制每次从源码重编。
- 新增 AOT 生成器时，确认输出目录在该清理路径下。

<!-- gitnexus:end -->
