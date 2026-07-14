# summer-core

**43 source files, 2266 lines, 5 tests.**
Base: `summer.core`. Referenced by EVERY framework module.

## OVERVIEW

Immutable IoC container, annotation contracts, and shared config binding. The only module every other module depends on. Engine-agnostic — both Runtime (reflection) and AOT (code-gen) engines consume its contracts.

## PACKAGE MAP

```
summer.core               # BeanContainer, DiEngine, Engine, Component, Provider, ApplicationRunner/State, ErrorCode, engine markers
├── annotation/            # @Configuration, @Bean, @ConditionalOnBean, @Replaces
├── bean/                  # BeanDefinition (sealed), ConfigPropertiesBean, Scope, RouteInfo, BeanClosure, ModuleIndex, SharedDependencyResolver, SharedConditionEvaluator
├── config/                # ConfigBinder, ConfigurationProperties, @DefaultValue, TypeConverter, ShutdownConfig, PageableProperties
├── exception/             # SummerException base + 10 concrete types (shared package with summer-exceptions module)
├── json/                  # SummerObjectMapper factory (Jackson, safe defaults, no polymorphic deser)
└── validation/            # Validator<T> interface, ValidationException
```

## WHERE TO LOOK

| Class | Purpose |
|-------|---------|
| `BeanContainer` | Immutable IoC container. `AutoCloseable`. Builder pattern — build once, never mutate. Reverse-order shutdown via `LinkedHashMap`. |
| `DiEngine` | Engine bootstrap. `Class.forName` on `GeneratedAotContext` (AOT) or `RuntimeBeanContainerBuilder` (RUNTIME). Auto-detection: debugger attach → RUNTIME, else AOT. |
| `Engine` | Enum `AOT` / `RUNTIME`. Passed to `SummerApplication.run()`. |
| `ConfigBinder` | `application.yml` → Java Record binding. Shared by both DI engines. Reads prefix section, applies `@DefaultValue`, converts via Jackson. |
| `BeanDefinition` | Sealed class (`permits ConfigPropertiesBean`). Single source of bean metadata across entire pipeline (identity → discovery → resolution → materialization). |
| `SharedDependencyResolver` | Topological sort + cycle detection (Kahn's algorithm). Resolves constructor params, @Bean method params, AOP interceptor deps. Shared by both engines. |
| `SharedConditionEvaluator` | Four-phase evaluation: collect @ConditionalOnBean → evaluate in topological order → resolve @Replaces → remove orphan @Bean products. |
| `Scope` | Functional interface defining the candidate universe for bean discovery. `classpath()`, `packageOf()`, `reachableFrom()`, `module()`. |
| `BeanClosure` | Jandex-only BFS for transitive dependency closure. Replaced former RuntimeComponentScanner.transitiveExpand + AOT equivalent. |
| `ModuleIndex` | Associates classes → originating module from each `META-INF/jandex.idx`. Enables module-scoped discovery. |
| `SummerObjectMapper` | Jackson `ObjectMapper` factory. Polymorphic deserialization disabled by default. `create()`, `createYaml()`, `createWith()`. |
| `Provider<T>` | Manual instance creation (substitute for prototype scope — singletons only in this framework). |
| `ApplicationRunner` | Lifecycle hook. Called after container refresh by web/gRPC/scheduled-task engines. |
| `ApplicationState` | Global `shuttingDown` flag (AtomicBoolean). Readiness probes check this. |
| `RuntimeDiMarker` / `AotDiMarker` | Marker beans for `@ConditionalOnBean(RuntimeDiMarker.class)` / `@ConditionalOnBean(AotDiMarker.class)`. Registered programmatically by each engine. |

## CONVENTIONS

- **Engine-agnostic design.** Core classes never import from `summer-runtime` or `summer-aot-engine`. All shared logic lives in `bean/` sub-package.
- **Sealed for safety.** `BeanDefinition` is sealed — only `ConfigPropertiesBean` may extend it. Prevents unbounded subclassing across engines.
- **Jandex for closure, reflection for resolution.** `BeanClosure` uses Jandex exclusively. Materialization can use reflection (Runtime) or code-gen (AOT).
- **Cross-module package sharing.** `summer.core.exception` package is shared with the `summer-exceptions` module. `ErrorCode` and base exception types (SummerException, AmbiguousBeanException, BeanCreationException, NoSuchBeanException) live in both modules.

## ANTI-PATTERNS

- ~~Importing from `summer-runtime` or `summer-aot-engine`~~ — core is engine-agnostic; engine-specific code breaks the contract.
- ~~Extending `BeanDefinition` outside sealed hierarchy~~ — only `ConfigPropertiesBean` may extend it. New bean types use composition, not subclassing.
- ~~Hardcoding Jandex dependency resolution~~ — Jandex indexing is for discovery/closure only. Materialization is the engine's choice.
- ~~Direct Jackson ObjectMapper construction~~ — use `SummerObjectMapper.create()` for consistent, safe defaults.
