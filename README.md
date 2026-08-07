# Summer Framework

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/version-0.1.0-blue.svg)]()
[![Java](https://img.shields.io/badge/Java-25+-blue.svg)]()
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

```yaml
# application.yml — development default; the Maven plugin flips it to aot at build time
summer:
  engine: runtime
```

```java
// Single entry point; engine resolved from configuration.
SummerApplication.run(args);
```

### Scaffold a project (recommended)

One-time setup in `~/.m2/settings.xml` — register the plugin group (so `summer:`
resolves to the plugin) and the GitHub Packages credentials:

```xml
<settings>
  <pluginGroups>
    <pluginGroup>com.github.dropguard</pluginGroup>
  </pluginGroups>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
  </servers>
</settings>
```

Then scaffold with a short command (the generated project inherits
`summer-build-parent`, declares the AOT plugin, and resolves artifacts from
GitHub Packages — nothing to hand-write):

```bash
mvn summer:create-app -DartifactId=myapp -DgroupId=com.example
```

(The same templates are available through the standard
`mvn archetype:generate -DarchetypeGroupId=com.github.dropguard
-DarchetypeArtifactId=summer-archetype` flow.)

### Enabling AOT in your project

Artifacts (framework + plugin) are published to **GitHub Packages** under
`com.github.dropguard`. Add the registry to your `pom.xml` (credentials: a
GitHub token with `read:packages`, in `settings.xml`):

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/DropGuard/Summer</url>
    </repository>
</repositories>
<pluginRepositories>
    <pluginRepository>
        <id>github</id>
        <url>https://maven.pkg.github.com/DropGuard/Summer</url>
    </pluginRepository>
</pluginRepositories>
```

**Inherit `summer-build-parent`** (the Quarkus-style convention). It supplies the
whole AOT toolchain: the Jandex index is bound automatically, and the parent's
`pluginManagement` provides the plugin version + `generate-aot` execution — so
enabling AOT is a single declaration, with no goals or phases to write:

```xml
<parent>
    <groupId>com.github.dropguard</groupId>
    <artifactId>summer-build-parent</artifactId>
    <version>0.1.0</version>
</parent>
...
<build>
    <plugins>
        <plugin>
            <groupId>com.github.dropguard</groupId>
            <artifactId>summer-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

The goal runs at `process-classes`: it generates the AOT context, compiles it into
`target/classes`, and rewrites `application.yml` to `summer.engine: aot`. See
`summer-realworld` for a complete example. (Maven Central publishing is deferred;
the registry above is the current distribution channel.)

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

Summer is designed for high-concurrency throughput using Java's virtual threads and a byte-level router that minimizes allocations. To see the framework's performance in action:

1. **Start the Showcase App**:
   ```bash
   cd summer-twitter
   mvn exec:java -Dexec.mainClass="com.github.dropguard.summer.twitter.Application"
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
*   Prototype scope (Singleton only)
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

For a fully working, runnable sample—featuring domain models, nested repositories, and database transaction management—run one of the demo applications bundled within this repository (`summer-twitter` showcase or `summer-realworld`):

```bash
cd summer-twitter
mvn exec:java -Dexec.mainClass="com.github.dropguard.summer.twitter.Application"
```

* * *

## 🌐 Inspired By

Summer is inspired by:

*   The middleware execution model of **Gin**
*   The declarative programming style of **Spring**

It is not a Spring replacement.  
It is not a better Spring.  
It is a narrower one.