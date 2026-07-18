# summer-runtime — Runtime DI Engine

## OVERVIEW

Reflection-based DI engine. Scans Jandex `.idx` files at startup, discovers beans annotated with `@Component`/`@Configuration`/`@Bean`, resolves constructor dependencies, instantiates reflectively, applies AOP proxies, registers routes — returns an immutable `BeanContainer`. Default engine for development (`-Dsummer.engine=RUNTIME`).

## STRUCTURE

Flat package `summer.runtime` (25 src, 18 test). No sub-packages in main. Test helpers under `summer.fixtures` + test-only `config/` sub-package.

```
summer.runtime/
├── RuntimeBeanContainerBuilder    # Orchestrator: load scan→discover→instantiate→wire→proxy→route
├── JandexIndexLoader              # Loads + merges META-INF/jandex.idx from classpath
├── RuntimeComponentScanner        # Filters indexed classes by @Component/@ConfigurationProperties + Scope
├── BeanDefinitionFactory          # Builds BeanDefinitions + AOP interceptor bindings from scanned classes
├── RuntimeBeanAdapter             # Bridge: Jandex ClassInfo → reflection metadata (@Bean methods, routes, exception handlers)
├── BeanInstantiator               # Reflective instantiation + constructor-arg resolution from BeanContainer
├── RuntimeAopProcessor            # Applies JDK proxy to beans with matching interceptors
├── ProxyFactory                  # JDK dynamic proxy generation (InvocationHandler + interceptor chain); builds InterceptedMethod from Method at proxy creation
├── HandlerFactory                 # Controller/exception-handler method → Handler with cold-start param provider compilation
├── RuntimeRouteRegistrar          # RouteInfo[] → HttpRouter.register() for @Get/@Post/...
├── RuntimeExceptionHandlerRegistrar # BeanDefinition.ExceptionHandlerEntry[] → ExceptionRegistry
├── RuntimeWebConfiguration        # @Configuration: registers RouteRegistrar + ExceptionHandlerRegistrar beans
├── HttpParameterResolverConfiguration # @Configuration: wires resolver chain (path/query/body/validation/throwable)
├── HttpParameterResolverChain     # Ordered chain of resolvers, fallback to ctx.body()
├── HttpParameterResolver          # Interface: supports() / resolve() / compile()
├── PathParamResolver              # @PathParam from URL segments
├── QueryParamResolver             # @QueryParam from query string (uses TypeConverter)
├── TypeParameterResolver          # HttpContext / Request injection
├── ThrowableResolver              # @ExceptionHandler Throwable-typed params from request attr
├── ValidatingParameterResolver    # @Valid body binding + validation
├── DefaultPageResolver            # Pagination: Pageable from query params
├── DefaultPageRequest             # DefaultPageRequest record
├── PageableConfiguration          # @Configuration: wires DefaultPageResolver
├── RuntimeDefaultValueResolver    # Applies @DefaultValue to @ConfigurationProperties records
└── TypeParameterResolver          # HttpContext / Request injection
```

## WHERE TO LOOK

| Entry point | File |
|---|---|
| **Engine entry** | `RuntimeBeanContainerBuilder.java` — `build()` method orchestrates whole pipeline |
| **Scanning** | `JandexIndexLoader.java` (load), `RuntimeComponentScanner.java` (filter) |
| **Bean construction** | `RuntimeBeanAdapter.java` (ClassInfo→metadata), `BeanDefinitionFactory.java` (BeanDefinition building) |
| **Instantiation** | `BeanInstantiator.java` — reflective newInstance + constructor-arg resolution |
| **AOP proxy** | `RuntimeAopProcessor.java` (dispatch), `ProxyFactory.java` (JDK Proxy generation) |
| **Route registration** | `RuntimeRouteRegistrar.java`, `RuntimeExceptionHandlerRegistrar.java` |
| **Handler creation** | `HandlerFactory.java` — cold-starts param providers, then hot-path is function call |
| **Param resolution** | `HttpParameterResolverChain.java` + resolvers in `*Resolver.java` files |

## CONVENTIONS

- **Flat package only** — all runtime classes live directly in `summer.runtime`. No sub-packages for main code.
- **Jandex-driven discovery** — modules must generate `META-INF/jandex.idx` via `jandex-maven-plugin`. No classpath directory scanning.
- **Side-effect-free constructors** — `BeanDefinitionFactory`, `ProxyFactory`, `HandlerFactory`, `RuntimeAopProcessor` are utility classes with private constructors. Accept deps as method params, not constructor injection.
- **Cold-start compilation** — `HandlerFactory.create()` pre-computes `Function<HttpContext, Object>[]` arrays at container build time so per-request param resolution is direct function calls, not annotation re-scanning.
- **ArchUnit boundary** — `java.lang.reflect` and `java.lang.invoke` imports are confined here (and `RuntimeDiMarker` interface in core). No other module may use reflection.

## ANTI-PATTERNS

- ~~Loading classes outside the Jandex index~~ — all bean discovery goes through `IndexView`. Direct `ClassPath` scanning or `ServiceLoader` in this module breaks the dual-engine contract.
- ~~Holding `IndexView` post-build~~ — `JandexIndexLoader` should be discarded after `RuntimeBeanContainerBuilder.build()`. The index is not needed at runtime.
- ~~Mutable `BeanContainer` after `build()` returns~~ — container is immutable by design. Post-build mutations bypass AOP and route registration.
- ~~Direct `Class.forName()` in production paths~~ — belongs only in `JandexIndexLoader` and `BeanInstantiator`. Route handlers and param resolvers must not load classes reflectively.
- ~~`ProxyFactory` bypass~~ — never call `ProxyFactory.create()` manually. Always go through `RuntimeAopProcessor.applyProxy()` which checks interceptor bindings first.
