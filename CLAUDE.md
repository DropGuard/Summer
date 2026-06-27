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
4. **Reflection confined to `summer-runtime`** — `java.lang.reflect.*`, `java.lang.invoke.*`, and reflective `Class` methods (`forName`, `newInstance`, `getDeclaredMethods`, etc.) are banned outside `summer.runtime` and `summer.test`. `DiEngine.create()` is the single exception: it uses `Class.forName` to load AOT-generated classes that do not exist at compile time.
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
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **Summer** (4205 symbols, 11000 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "main"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/Summer/context` | Codebase overview, check index freshness |
| `gitnexus://repo/Summer/clusters` | All functional areas |
| `gitnexus://repo/Summer/processes` | All execution flows |
| `gitnexus://repo/Summer/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
