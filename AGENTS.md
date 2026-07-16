# PROJECT KNOWLEDGE BASE

**Generated:** 2026-07-14
**Stack:** Java 26+ · Maven multi-module · Netty · Jandex · JDK Dynamic Proxies · JUnit 5
**Description:** Minimalist JDK-native CRUD framework. Clarity-first reconstruction of Spring-like runtime — singleton DI, annotation routing, virtual threads.

## STRUCTURE

```
summer-framework/
├── summer-core/           # DI container, annotations, config binding
├── summer-web/            # Router, Middleware, Handler, WS abstractions
│   ├── summer-web-http/   #   HTTP types (thin)
│   ├── summer-web-netty/  #   Netty server impl
│   ├── summer-web-middleware/  # CORS, Logging, Metrics
│   └── summer-web-websocket/   # Radix/Map WS routers
├── summer-runtime/        # Runtime DI engine (Jandex + reflection)
├── summer-aot-engine/     # AOT code-gen DI engine
├── summer-boot/           # SummerApplication entry point
├── summer-aop/            # JDK proxy interceptor chain
├── summer-tx/             # Transaction mgmt (REQUIRED only)
├── summer-data-jdbc/      # JDBC template + RowModel
├── summer-data-redis/     # Redis client
├── summer-grpc/           # gRPC client + server
├── summer-maven-plugin/   # AOT codegen + Jandex indexing
├── summer-test/           # @SummerTest, @Mock test infra
├── summer-tck/            # Behavioral tests (Runtime + AOT engines)
├── summer-tck-fixtures/   # Shared test fixtures
├── summer-archunit/       # Architecture constraint tests
├── summer-exceptions/     # Shared exception types
├── summer-example/        # CRUD demo (JDBC, gRPC, WS)
├── summer-realworld/      # RealWorld clone (hurl e2e)
├── summer-twitter/        # Twitter clone (PG + Redis)
└── summer-benchmark/      # k6 load tests (Summer vs Spring Boot)
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| App entry point | `summer-boot/.../boot/SummerApplication.java` |
| DI engine selection | `summer-core/.../core/DiEngine.java` |
| Runtime DI (reflection) | `summer-runtime/.../runtime/RuntimeBeanContainerBuilder.java` |
| AOT DI (code-gen) | `summer-aot-engine/.../aot/AotEngine.java` |
| HTTP server (Netty) | `summer-web-netty/.../server/NettyServerRunner.java` |
| Router builder | `summer-web/.../web/HttpRouter.java` + Builder |
| AOP processor | `summer-runtime/.../runtime/RuntimeAopProcessor.java` |
| Test container builder | `summer-test/.../test/Testing.java` |
| Dual-engine TCK tests | `summer-tck/src/test/java/summer/tck/` |
| Architecture rules | `summer-archunit/src/test/java/summer/arch/` |
| Example app | `summer-example/src/main/java/summer/example/` |

## CONVENTIONS

- **Constructor injection only** — no field/setter injection. Fail-fast on ambiguity.
- **Interface-based AOP** — JDK dynamic proxy only. No CGLIB. Internal `this.method()` calls bypass proxy.
- **Records for config/data** — `@ConfigurationProperties` and `@RowModel` must be Java Records.
- **Dual DI engine** — RUNTIME (Jandex+reflection, dev) or AOT (compile-time wire(), prod). Switched via `-Dsummer.engine`.
- **Virtual threads** — HTTP dispatch on `Thread.startVirtualThread`. `WebContext`/`Request` not thread-safe.
- **Singletons only** — no prototype scope. Use `Provider<T>` for manual creation.
- **Explicit middleware** — global middleware registered via `SummerApplication.apply()`. Route-level via `Router.Builder.mount()`.
- **REQUIRED-only transactions** — no distributed/XA.
- **YAML config** — `application.yml` bound to `@ConfigurationProperties` records. Nested under server/data/ keys.
- **Tests** — JUnit 5 + Mockito. `@SummerTest(modules = "...")` derives the bean scope from the test's own module (plus declared modules/packages); Runtime engine in dev. `@Mock` injects Mockito mocks. `*IT.java` for integration (Failsafe).

## ANTI-PATTERNS

- ~~Reflection outside `summer-runtime`~~ (ArchUnit enforced)
- ~~CGLIB/ByteBuddy/ClassGraph deps~~ (ArchUnit enforced)
- ~~ConcurrentHashMap or ServiceLoader in production~~ (ArchUnit enforced)
- ~~CJK characters in comments~~ (ArchUnit enforced)
- ~~Field/setter injection~~ (design constraint)
- ~~Catch `Throwable`~~ — catch specific types. Broad `catch (Exception e)` is tolerated but discouraged.
- ~~Empty catch blocks~~ — at minimum log the exception.
- ~~`System.out/err` in production~~ — use SLF4J or MetricsRegistry.
- ~~`@SuppressWarnings("unchecked")`~~ — 17 occurrences, justify in comment.
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
mvn compile exec:java -pl summer-example -am  # Run example app
```

## NOTES

- JDK 26 required (`--sun-misc-unsafe-memory-access=allow` for Netty/AOT).
- No Maven wrapper — CI uses `setup-java` which auto-installs Maven.
- No `module-info.java` yet — all runs on classpath.
- `summer-exceptions` module uses `summer.core.exception` package (cross-module package sharing with core).
- `summer-tck` is test-only (no `src/main`). `summer-tck-fixtures` is main-only (no `src/test`).
- Proto files at `src/main/proto/` (non-standard) not `src/main/proto/`.

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
