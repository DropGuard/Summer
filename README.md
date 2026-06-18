# Summer Framework

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)]()
[![Java](https://img.shields.io/badge/Java-26+-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()

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

## 🏗 Architecture Overview

```text
+-------------------+
|     HTTP Layer    |
|  Middleware + Router |
+-------------------+
          ↓
+-------------------+
|   Application     |
|  IoC + AOP + Tx   |
+-------------------+
          ↓
+-------------------+
|   Persistence     |
|  JDBC / Redis     |
+-------------------+
          ↓
+-------------------+
|   RPC             |
|  gRPC / WebSocket |
+-------------------+
```



## ⚡ Dual DI Engine

Summer's DI container has two interchangeable implementations:

| | Runtime Engine | AOT Engine |
|---|---|---|
| **How it works** | Scans Jandex indexes at startup, resolves dependencies via reflection | Generates a `wire()` method at compile time that calls constructors directly |
| **When to use** | Development, debugging | Production, fast startup |
| **Activation** | `SummerApplication.run(Engine.RUNTIME, args)` | `SummerApplication.run(Engine.AOT, args)` |
| **Startup cost** | ~200ms (classpath scanning) | ~10ms (direct constructor calls) |
| **Failure point** | `NoSuchBeanException` at runtime | Compilation error (fail-fast) |

Both engines share the same annotation contract (`@Component`, `@Configuration`, `@Bean`, `@ConditionalOnBean`). Switching requires no code changes — only the `Engine` enum:

```java
// Default — auto-detect AOT, fall back to Runtime
SummerApplication.run(args);

// Explicit Runtime
SummerApplication.run(Engine.RUNTIME, args);

// Explicit AOT
SummerApplication.run(Engine.AOT, args);
```

The AOT engine works by scanning Jandex indexes at build time via the `summer-maven-plugin`, resolving the dependency graph, and emitting a `GeneratedAotContext` class that wires all beans in a single `wire()` method. At runtime, this class replaces the runtime scanner entirely.

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
4. **Stateless by default, Context by necessity.** All components (`@Component`) are instantiated as singletons. Request state flows explicitly as method arguments (`WebContext`). However, for cross-cutting infrastructural state like Database Transactions, Summer leverages safe `ThreadLocals` backed by ephemeral Virtual Threads to prevent method signature pollution.
5. **Composition over Inheritance.** Small interfaces are preferred over abstract base classes. Summer avoids deep inheritance hierarchies.
6. **Minimal feature surface.** Summer core is intentionally minimal and does not bundle validation or security. Validation is provided via optional modules.
7. **Code as Configuration / Code as Documentation.** Summer avoids externalizing every possible tweak into YAML or JSON. Moving all runtime logic into configuration files fragments the application's intent and makes it harder to trace. Instead, Summer encourages utilizing fluent builders and explicit code to configure server parameters (like timeouts). This keeps logic cohesive and ensures that the configuration is as readable and version-controlled as the rest of the application.
8. **JDK 26 baseline.**

* * *

## 📦 Supported Features (v0.1)

*   Singleton IoC container
*   Constructor injection (records seamlessly supported)
*   `@Configuration` + `@Bean` (framework-standard bean registration)
*   `@ConfigurationProperties` + `@DefaultValue` (type-safe config binding to Java Records)
*   Interface-based AOP
*   `@Transactional` (single datasource, REQUIRED only)
*   Middleware-based HTTP handling (with explicit Annotation Routing)
*   Basic annotation routing (`@RestController`, `@Get`, `@Post`, `@Put`, `@Delete`)
*   JSON request/response binding
*   YAML configuration mapped to Java Records (`application.yml`)
*   JDBC template with `@RowModel` (record-based row mapping)
*   Redis client (`@EnableRedis`)
*   gRPC client & server
*   WebSocket support
*   Virtual thread-based HTTP request handling (Project Loom)
*   Global exception middleware
*   Optional validation system (`summer-validation-hv`)
*   Prometheus-compatible metrics (`MetricsMiddleware`)

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
    public String metrics(WebContext ctx) {
        ctx.response().setHeader("Content-Type", "text/plain; version=0.0.4");
        return registry.scrape();
    }
}
```

Once exposed, you can point your Prometheus instance to `/metrics` to begin scraping.

* * *

## 📈 Performance Benchmarking

Summer is designed for high-concurrency throughput using Java's virtual threads and a byte-level router that minimizes allocations. To see the framework's performance in action:

1. **Start the Example App**:
   ```bash
   cd summer-example
   mvn exec:java -Dexec.mainClass="summer.example.Application"
   ```

2. **Run a Load Test** (using `wrk`):
   ```bash
   # Simulate 100 concurrent users for 30 seconds using 4 OS threads
   wrk -t4 -c100 -d30s http://localhost:8080/users/1
   ```

3. **Monitor Metrics**:
   While the test is running, open `http://localhost:8080/_system/metrics` in your browser to see real-time stats:
   - `summer_requests_active`: Current concurrent requests being handled by virtual threads.
   - `summer_requests_total`: Throughput achieved.

### Why Summer is Fast
- **Virtual Threads**: Every request is a lightweight thread; no thread pool exhaustion.
- **Byte-Level Router**: Routing compares path segments as raw bytes, avoiding String allocation for the path itself. Path parameters still require String creation.
- **Minimalistic Core**: No deep interceptor chains or complex proxy logic for standard requests.

* * *

## ❌ Intentionally Unsupported (Non-Goals)

Summer is an experiment in reduction — not expansion. If a feature is not listed above, it is intentionally unsupported.

*   Field injection (`@Inject`, `@Autowired`, `@Value`) & Setter injection (use constructor injection exclusively)
*   Circular dependency resolution
*   Class-based proxying (CGLIB)
*   Prototype scope (Singleton only; use `Provider<T>` for manual instance creation)
*   Multi-threaded application startup (context initialization is single-threaded by design)
*   Distributed/XA transactions
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
> `WebContext` and `Request` are **not thread-safe**. Since Summer dispatches each request on an isolated virtual thread, internal state is safely confined to the call stack. 
> 
> If you initiate a background task (e.g., using an ExecutorService), you **must not** pass the `WebContext` or `Request` object directly to the other thread. Instead, extract the required data (strings, parsed objects, etc.) and pass only those. This mirrors the design of frameworks like Gin.

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

## 🚀 Getting Started

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

// 3. Controller
@RestController("/users")
public class UserController {
    
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @Get("/{id}")
    public User getUser(@PathParam("id") String id) {
        // Zero-reflection parameter binding
        return userService.getUser(id);
    }

    @Post("")
    public User createUser(User user) {
        // Automatic JSON body binding & validation
        return userService.createUser(user);
    }
}

// 4. Global Exception Handling
@Component
public class GlobalErrorHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public void handleNotFound(WebContext ctx, UserNotFoundException e) {
        ctx.response().setStatusCode(404);
        ctx.ok(new ErrorResponse("Not Found", e.getMessage()));
    }
}
```

For a fully working, runnable sample—featuring domain models, nested repositories, and database transaction management—run the `summer-example` module bundled within this repository:

```bash
cd summer-example
mvn exec:java -Dexec.mainClass="summer.example.Application"
```

* * *

## 🌐 Inspired By

Summer is inspired by:

*   The middleware execution model of **Gin**
*   The declarative programming style of **Spring**

It is not a Spring replacement.  
It is not a better Spring.  
It is a narrower one.