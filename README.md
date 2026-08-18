# Summer Framework

[![Build Status](https://img.shields.io/github/actions/workflow/status/DropGuard/Summer/maven.yml)](https://github.com/DropGuard/Summer/actions/workflows/maven.yml)
[![Version](https://img.shields.io/badge/version-999--SNAPSHOT-blue.svg)](https://github.com/DropGuard/Summer/releases)
[![Java](https://img.shields.io/badge/Java-25+-blue.svg)](https://github.com/DropGuard/Summer)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://github.com/DropGuard/Summer/blob/main/LICENSE)

> A minimalist JDK-native framework for building CRUD APIs.

Summer is a clarity-first reconstruction of the minimal runtime model behind CRUD services.
> The essential mechanics behind most CRUD-oriented services do not require thousands of classes, deep inheritance hierarchies, or complex startup lifecycles.

This project is a clarity-first re-implementation of the minimal core used in most CRUD services.

* * *

## ✨ Philosophy: Explicit Execution Over Implicit Magic

Summer is built around one core idea:

> Runtime behavior is modeled as an explicitly visible chain.

**HTTP Execution:**
```text
HttpServer → MiddlewareChain → Router → Handler
```

**Service Method Execution:**
```text
Proxy → InterceptorChain → Target Method
```

Instead of relying on deep container magic, there is no hidden execution layer beyond this model. Summer keeps execution explicit and predictable.


Cross-cutting (AOP proxy chain, @Transactional) intercept every layer above
the IoC container — they are interceptors, not layers.

## 🚀 Getting Started

Three commands cover the whole lifecycle — scaffold, develop, ship. They are Maven
goals from `summer-maven-plugin`, so nothing extra to install beyond Maven.

| Step | Command | What it does |
|---|---|---|
| 1. Scaffold | `summer create myapp` | Generate a new project (inherits `summer-build-parent`, wires AOT + Jandex) |
| 2. Develop | `summer dev` | Hot-reload dev loop on `:8080` (TCP proxy + child JVM) |
| 3. Ship | `summer build` | AOT build → runnable fat jar (`java -jar`) |

Summer is published to Maven Central under `io.github.dropguard`, so no special repository configuration is required. Run one of the bundled demos (`summer-twitter` / `summer-realworld` / `summer-issue-tracker`) to see Summer in practice.

* * *

## ⚡ Dual DI Engine

Summer is a Java web framework with two interchangeable DI engines: reflection-based
runtime wiring and compile-time code generation. **AOT is the production engine** — the
`summer-maven-plugin` generates the wiring at build time, so startup is direct
constructor calls with no reflection. **Runtime is the test engine** — it scans Jandex
indexes at startup and is the default during development. Dev mode is an escape hatch
for debugging: pin either engine explicitly with `-Dsummer.engine`.

| | Runtime Engine | AOT Engine |
|---|---|---|
| **Wiring** | Reflection at startup (Jandex index scan) | Compile-time code generation |
| **Role** | Test engine (development default) | Production engine (build-time default) |
| **Startup cost** | ~200ms (classpath scanning) | ~10ms (direct constructor calls) |
| **Failure point** | `NoSuchBeanException` at runtime | Compilation error (fail-fast) |

Both engines share the same annotation contract (`@Component`, `@Configuration`, `@Bean`, `@ConditionalOnBean`, `@ConfigMapping`). Switching requires no code changes: `application.yml` defaults to `runtime` for development, and the Maven plugin rewrites it to `aot` at build time — so production builds run AOT.

**Engine classpath constraint** (Quarkus-aligned): the AOT engine is wired at build time and needs no index at runtime — it is the fat-jar engine. The Runtime engine scans `META-INF/jandex.idx` at startup, so it belongs on the exploded classpath (dev mode, tests) where every jar carries its own index; a shaded fat jar collapses the index files and is AOT-only. Pin the engine with `-Dsummer.engine` / `SUMMER_ENGINE` (precedence: system property > environment variable > `application.yml` > default).

```yaml
# application.yml — development default; the Maven plugin flips it to aot at build time
summer:
  engine: runtime
```

```java
// Single entry point; engine resolved from configuration.
SummerApplication.run(args);
```


## 🛠 Daily Development

The three commands above are the everyday loop. This section holds the
details behind them for when you need them.

* * *

### Dev Mode (`summer:dev`)

Hot-reload development loop: a TCP proxy listens on port `8080` and forwards
traffic to your app running in a child JVM. On a source change it kills the child
("eager kill"), recompiles the changed files, and lazily reboots — the next request
is held until the fresh backend is ready ("lazy compile"):

```bash
mvn summer:dev
```

- **Classpath**: the app runs on the **test classpath** (same choice as Quarkus dev
  mode), so test-scoped dependencies are available while developing.
- **Engine**: defaults to the Runtime engine (the dev engine) per `application.yml`;
  pin either engine with `mvn summer:dev -Dsummer.engine=aot` — the override is
  forwarded to the child JVM as the `SUMMER_ENGINE` environment variable.
- **Reload**: edit a file under `src/main/java` — the watcher kills the child
  immediately, breaking any in-flight keep-alive pipe; the next request triggers a
  recompile and reboot. Changes under `src/main/resources` (e.g. `application.yml`)
  are copied into the output dir and restart the child the same way — no manual
  restart needed for config tweaks (the log shows the changed files, the new
  backend port, and the reload time).
- **Main class**: auto-detected from the Jandex index, or set
  `<summer.mainClass>` in your `pom.xml`.
- **Port**: `<summer.dev.port>` (default `8080`).

### Scaffold a project (recommended)

Summer projects are best created using the official `summer` CLI. The CLI is incredibly fast and embeds the project templates directly into the binary.

**1. Install the CLI:**
```bash
# macOS / Linux
curl -fsSL https://raw.githubusercontent.com/DropGuard/summer-cli/main/install.sh | bash
```

```powershell
# Windows (Run as Administrator)
Invoke-WebRequest -Uri "https://github.com/DropGuard/summer-cli/releases/latest/download/summer-windows-amd64.exe" -OutFile "$env:SystemRoot\system32\summer.exe"
```

**Uninstallation:** Because the CLI is a clean, single-file binary, uninstalling is as simple as deleting the executable (`sudo rm /usr/local/bin/summer` on Unix, or `Remove-Item "$env:SystemRoot\system32\summer.exe"` on Windows).

**2. Generate and Run:**
```bash
# Instantly generate the project
summer create my-first-api --group-id com.example

# Start the dev server (automatically fetches dependencies via Maven)
cd my-first-api
summer dev
```

### Enabling AOT

For a hand-written `pom.xml`, enabling AOT is as simple as inheriting `summer-build-parent`. It binds the Jandex index and the `generate-aot` execution automatically (both are in its `<build><plugins>`), so inheriting the parent is the whole AOT setup — no plugin declaration of your own:

```xml
<parent>
    <groupId>io.github.dropguard</groupId>
    <artifactId>summer-build-parent</artifactId>
    <version>0.1.0</version>
</parent>
```

The goal runs at `process-classes`: it generates the AOT context, compiles it into
`target/classes`, and rewrites `application.yml` to `summer.engine: aot`. See
`summer-realworld` for a complete example.

* * *

## 🎯 The Hypothesis (What Summer Tries to Prove)

For a typical CRUD application, the core runtime model can be reduced to:

1. Constructor-based dependency injection
2. A small HTTP routing layer
3. Interface-based method interception
4. A minimal transaction boundary (REQUIRED only)
5. Explicit middleware execution

No automatic configuration layers. No subclass-based proxying. No complex `BeanPostProcessor` / `InitializingBean` lifecycle hook mazes. No classpath-driven magic.

* * *

## 🔧 Design Principles & Constraints

Summer intentionally enforces strict architectural constraints. If something requires implicit behavior to work, it likely does not belong in Summer:

1. **Clarity over convenience.** (No hidden initialization phases).
2. **Constructor injection only.** Fail-fast on ambiguity. No circular dependency resolution.
3. **Interface-first AOP (JDK dynamic proxy).** No subclass-based proxying (CGLIB).
4. **Stateless by default, Context by necessity.** All components (`@Component`) are instantiated as singletons. Request state flows explicitly as method arguments (`HttpContext`). However, for cross-cutting infrastructural state like Database Transactions, Summer leverages safe `ThreadLocals` backed by ephemeral Virtual Threads to prevent method signature pollution.
5. **Composition over Inheritance.** Small interfaces are preferred over abstract base classes. Summer avoids deep inheritance hierarchies.
6. **Minimal feature surface.** Summer core is intentionally minimal and does not bundle validation or security. Validation is provided via optional modules.
7. **Code as Configuration / Code as Documentation.** Summer avoids externalizing every possible tweak into YAML or JSON. Moving all runtime logic into configuration files fragments the application's intent and makes it harder to trace. Instead, Summer encourages utilizing fluent builders and explicit code to configure server parameters (like timeouts). This keeps logic cohesive and ensures that the configuration is as readable and version-controlled as the rest of the application.
8. **JDK 25 baseline.**

* * *

## 📦 Supported Features (v0.1)

*   Singleton IoC container
*   Constructor injection (records seamlessly supported)
*   `@Configuration` + `@Bean` (framework-standard bean registration)
*   `@ConfigMapping` + `@WithDefault` (type-safe YAML config binding to interfaces)
*   `@ConditionalOnBean` / `@Replaces` (declarative conditional assembly)
*   Interface-based AOP
*   `@Transactional` (single datasource, REQUIRED only)
*   Middleware-based HTTP handling (with explicit Annotation Routing)
*   Basic annotation routing (`@RestController`, `@Get`, `@Post`, `@Put`, `@Delete`)
*   JSON request/response binding + built-in validation (`ctx.validatedBody`, Jakarta Validation + avaje-validator)
*   YAML configuration (`application.yml`, `${VAR}` / `${VAR:-default}` placeholders)
*   JDBC template with `@RowModel` (record-based row mapping)
*   Redis client (`summer-data-redis` + starter)
*   gRPC client & server
*   WebSocket support
*   Virtual thread-based HTTP request handling (Project Loom)
*   Global exception middleware
*   Prometheus-compatible metrics (`MetricsMiddleware`, `summer-web-middleware`)

* * *

## 📊 Observability & Metrics

Summer provides a lightweight observability suite through the `MetricsRegistry` and `MetricsMiddleware`. It tracks concurrent requests, total throughput, error counts, and system uptime.

To enable observability, simply register the `MetricsMiddleware` in your application context. The metrics are exported in plain-text format compatible with **Prometheus**.

**Example: Exposing Metrics via Controller**
```java
@RestController("/_system")
public class SystemController {
    private final MetricsRegistry registry;

    public SystemController(MetricsRegistry registry) {
        this.registry = registry;
    }

    @Get("/metrics")
    public void metrics(HttpContext ctx) {
        ctx.setHeader("Content-Type", "text/plain; version=0.0.4");
        ctx.text(HttpStatus.OK, registry.scrape());
    }
}
```

Once exposed, you can point your Prometheus instance to `/metrics` to begin scraping.

* * *

## 📈 Performance Benchmarking

Summer is built for high-concurrency throughput using virtual threads and a
byte-level router that minimizes allocations. The benchmark suite in
`summer-benchmark` compares two deliberately minimal, identical apps — one on
Summer (Netty), one on Spring Boot (Tomcat) — isolated in Docker containers
with identical resource limits (2 CPUs / 512MB) and the same stack (Virtual
Threads, Jackson).

### Results

| Metric | Spring Boot 4.0 (Tomcat) | Summer (Netty) | Improvement |
|---|---|---|---|
| Requests/sec (RPS) | 15,030 | 40,604 | **+170%** |
| Avg Latency (ms) | 6.56 | 2.25 | **-66%** |
| P95 Latency (ms) | 24.69 | 4.67 | **-81%** |

Full methodology, constraints, and the orchestrator script live in
`summer-benchmark/` (`python run-benchmarks.py`).

### Why Summer is Fast
- **Virtual Threads**: every request is a lightweight thread; no thread-pool
  exhaustion under concurrency.
- **Byte-Level Router**: routing compares path segments as raw bytes, avoiding
  String allocation for the path itself.
- **Minimalistic Core**: no deep interceptor chains or proxy logic for standard
  requests — less work per request is why the RPS and latency numbers above
  hold.

* * *

## ❌ Intentionally Unsupported (Non-Goals)

Summer is an experiment in reduction — not expansion. If a feature is not listed in the **Supported Features** section, it is intentionally unsupported.

*   Field injection (`@Inject`, `@Autowired`, `@Value`) & Setter injection (use constructor injection exclusively)
*   Circular dependency resolution
*   Class-based proxying (CGLIB)
*   Prototype scope (Singleton only)
*   Multi-threaded application startup (context initialization is single-threaded by design)
*   Distributed/XA transactions
*   Nested transactions (REQUIRED only — a `@Transactional` call inside an active transaction
    fails loudly with `SummerTransactionException`)
*   Classpath-based guessing (e.g., "if DataSource is on classpath, auto-configure JdbcTemplate"). Explicit engine selection with `@ConditionalOnBean` is supported — components may follow the active engine via marker beans.
*   Bean post-processor ecosystem & complex lifecycle hooks
*   Security module
*   Built-in thread pool / executor service (use virtual threads or bring your own)
*   Framework-level custom ClassLoader Hot-Reload (rely on JVM hotswap instead)
*   Ecosystem compatibility (Spring Data, Actuator, Starters, etc.)

* * *

## 🧵 Threading Model: Virtual Threads, No Built-in Pool

Summer's HTTP server dispatches every incoming request on a **virtual thread** (`Thread.startVirtualThread`). Virtual threads are lightweight, JVM-managed threads that can handle millions of concurrent connections with minimal memory overhead.

Summer **does not** provide a built-in thread pool or `ExecutorService`. This is intentional:

*   The framework handles HTTP dispatch; **you** handle your own concurrency.
*   If your business logic requires a thread pool, create one explicitly in your service layer.
*   This avoids framework-managed threading magic and keeps concurrency decisions visible in your code.

```java
// You're free to use any concurrency model in your own code:
ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
pool.submit(() -> myBlockingIoTask());
```

> **⚠️ THREAD SAFETY WARNING**
>
> `HttpContext` and `Request` are **not thread-safe**. Since Summer dispatches each request on an isolated virtual thread, internal state is safely confined to the call stack. 
> 
> If you initiate a background task (e.g., using an ExecutorService), you **must not** pass the `HttpContext` or `Request` object directly to the other thread. Instead, extract the required data (strings, parsed objects, etc.) and pass only those. This mirrors the design of frameworks like Gin.

* * *

## 🧠 Why Interface-Based AOP?

Summer uses JDK dynamic proxies only. If a bean annotated with AOP-related annotations does not implement an interface, Summer will fail at startup.

This keeps the core predictable and avoids subclass-based proxy complexity. Explicit behavior via contracts is always preferred over implicitly intercepting hidden class methods.

> **⚠️ CAUTION: The AOP Trap**
>
> Because Summer uses standard JDK dynamic proxies, **internal method calls** (e.g., `this.doSomething()`) will bypass the proxy and the interceptors. If you need transaction management, ensure the method is called through its interface from another bean.

## 🛡️ Security & Middleware

Summer intentionally does not ship with a full security framework or built-in rate limiting. 

Authentication and authorization are expected to be implemented at the HTTP boundary via middleware. This keeps the framework minimal and avoids baking complex security policies into the core. You can write middlewares to enhance:

*   Authentication / Authorization
*   Rate limiting
*   CORS
*   Request logging
*   Error mapping

These are just functions. No container hooks required.

* * *

## ⚖️ When to use Summer vs Spring

**When to use Summer:**
*   You want a **minimal** stack for small to medium CRUD APIs
*   You value **explicit execution**, clarity, and a small dependency surface
*   You’re happy to implement simple features as **middleware**
*   You don’t need heavy ecosystem modules

**When NOT to use Summer:**
*   You need **Spring Security** / OAuth2 / SSO / advanced RBAC
*   You rely heavily on Spring’s ecosystem (Starters, Spring Data, Actuator, etc.)
*   You need complex transaction semantics or large-scale integration
*   You need complex lifecycle hooks

* * *

## 🔬 What Summer Actually Demonstrates

Summer is not primarily about replacing Spring.

It is a didactic reconstruction of the minimal runtime model
behind most modern CRUD frameworks.

It exists to make the invisible visible.

## 🧪 A Minimal Example

Here is what Summer looks like in practice. Notice the strict constructor injection and the explicit `Request` object parsing.

> **Note:** `@Component` classes must have exactly one public constructor.

```java
// 1. Repository
@Component
public class UserRepository {
    public User findById(String id) {
        // DB Access
        return new User(id, "Alice");
    }
}

// 2. Service
public interface UserService {
    User getUser(String id);
}

@Component
public class UserServiceImpl implements UserService {
    
    private final UserRepository repository;

    public UserServiceImpl(UserRepository repository) {
        this.repository = repository;
    }
    
    @Transactional
    @Override
    public User getUser(String id) {
        return repository.findById(id);
    }
}

// 3. Controller — Gin-style: write to the context, return void
@RestController("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Get("/{id}")
    public void getUser(HttpContext ctx, @PathParam("id") String id) {
        // Zero-reflection parameter binding
        ctx.json(HttpStatus.OK, userService.getUser(id));
    }

    @Post("")
    public void createUser(HttpContext ctx) {
        // Automatic JSON body binding & validation
        User user = ctx.validatedBody(User.class);
        ctx.json(HttpStatus.CREATED, userService.createUser(user));
    }
}

// 4. Global Exception Handling
@Component
public class GlobalErrorHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public void handleNotFound(UserNotFoundException e, HttpContext ctx) {
        ctx.status(HttpStatus.NOT_FOUND);
        ctx.json(HttpStatus.NOT_FOUND, new ErrorResponse("Not Found", e.getMessage()));
    }
}
```

For a fully working, runnable sample—featuring domain models, nested repositories, and database transaction management—run one of the demo applications bundled within this repository (`summer-twitter` showcase, `summer-realworld`, or `summer-issue-tracker`):

```bash
cd summer-twitter
mvn exec:java -Dexec.mainClass="com.github.dropguard.summer.twitter.Application"
```

* * *

## 🌐 Inspired By

Summer is inspired by:

*   The middleware execution model of **Gin**
*   The declarative programming style of **Spring**
*   The build-time AOT convention and dev-mode ergonomics of **Quarkus**

It is not a Spring replacement.  
It is not a better Spring.  
It is a narrower one.