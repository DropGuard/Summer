# summer-core

Base: `summer.core`. Referenced by EVERY framework module.

## OVERVIEW

Immutable IoC container, annotation contracts, and shared config binding. The only module every other module depends on. Engine-agnostic — both Runtime (reflection) and AOT (code-gen) engines consume its contracts. The shared discovery pipeline (`Discovery`, `BeanEnrichment`, `SharedConditionEvaluator`) and the `DiEngine` bootstrap live in `summer-engine`, not here.

## PACKAGE MAP

```
summer.core               # BeanContainer, Component, Engine, ErrorCode, ApplicationRunner/State,
                          # RuntimeDiMarker/AotDiMarker, ShutdownContext, Sealable/FrozenState, @Internal
├── annotation/           # @Configuration, @Bean, @ConditionalOnBean, @Replaces, @Order
├── bean/                 # BeanDefinition (sealed), ConfigPropertiesBean, InjectionParameter,
│                         # MockedBean, RouteInfo, SharedDependencyResolver
├── config/               # ConfigBinder, @ConfigMapping, @WithDefault, @WithName, TypeConverter,
│                         # FrameworkConfig, ShutdownConfig, PageableProperties
├── data/                 # Page, PageRequest, LimitOffsetPageRequest
├── exception/            # SummerException base + 13 concrete types (no separate exceptions module)
├── json/                 # SummerObjectMapper — static Jackson ObjectMapper factory (safe defaults,
│                         # no polymorphic deser)
├── spi/                  # RouteRegistrar, RouteRegistrarLoader, RouteRegistry
├── util/                 # Regexes
└── validation/           # Validator<T> interface + Result (ValidationException lives in summer-web)
```

## WHERE TO LOOK

| Class | Purpose |
|-------|---------|
| `BeanContainer` | Immutable IoC container. `AutoCloseable`. Builder pattern — build once, never mutate. Reverse-order shutdown via `LinkedHashMap`. |
| `Engine` | Enum `AOT` / `RUNTIME`. Passed to `SummerApplication.run()`. Engine bootstrap (`DiEngine`) lives in `summer-engine`. |
| `ConfigBinder` | `application.yml` → Java binding. Shared by both DI engines. Reads prefix section, applies `@WithDefault`, converts via Jackson. |
| `BeanDefinition` | Sealed class (`permits ConfigPropertiesBean`). Single source of bean metadata across the entire pipeline (identity → discovery → resolution → materialization). |
| `SharedDependencyResolver` | Topological sort + cycle detection (Kahn's algorithm). Resolves constructor params, @Bean method params, AOP interceptor deps. Shared by both engines. |
| `SummerObjectMapper` | Jackson `ObjectMapper` factory: `create()`, `create(Consumer)`, `createYaml()`, `createYaml(Consumer)`. Polymorphic deserialization disabled by default. |
| `ApplicationRunner` | Lifecycle hook. Called after container refresh by web/gRPC/scheduled-task engines. |
| `ApplicationState` | Global `shuttingDown` flag (AtomicBoolean). Readiness probes check this. |
| `RuntimeDiMarker` / `AotDiMarker` | Marker beans for `@ConditionalOnBean(RuntimeDiMarker.class)` / `@ConditionalOnBean(AotDiMarker.class)`. Registered programmatically by each engine. |

## CONVENTIONS

- **Engine-agnostic design.** Core classes never import from `summer-runtime` or `summer-aot-engine`. Cross-engine shared logic beyond the container itself (discovery, closure, condition evaluation) lives in `summer-engine`, not in core.
- **Sealed for safety.** `BeanDefinition` is sealed — only `ConfigPropertiesBean` may extend it. Prevents unbounded subclassing across engines.
- **Single exceptions module.** Shared exceptions live in `core/exception/` here; there is no separate exceptions module.
- **Jandex for discovery, engine's choice for materialization.** Jandex indexing feeds discovery/closure in the pipeline; materialization is reflection (Runtime) or code-gen (AOT).

## ANTI-PATTERNS

- ~~Importing from `summer-runtime` or `summer-aot-engine`~~ — core is engine-agnostic; engine-specific code breaks the contract.
- ~~Extending `BeanDefinition` outside sealed hierarchy~~ — only `ConfigPropertiesBean` may extend it. New bean types use composition, not subclassing.
- ~~Hardcoding Jandex dependency resolution~~ — Jandex indexing is for discovery/closure only. Materialization is the engine's choice.
- ~~Direct Jackson ObjectMapper construction~~ — use `SummerObjectMapper.create()` for consistent, safe defaults.
