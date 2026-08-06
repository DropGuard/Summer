# Summer Framework — Code Audit

> Generated 2026-08-04. Three-agency systematic audit: public API surface, architecture/abstractions, internal code quality.

---

## 1. Critical — Functional Bugs & Divergences

| # | Issue | Location |
|---|-------|----------|
| 1.1 | **AOT vs runtime PATH/QUERY param binding-key divergence.** AOT reads `param.name` (Java parameter name), runtime reads `param.bindingName()` (`@PathParam`/`@QueryParam` value). `@PathParam("userId") Long id` resolves correctly on runtime, silently wrong on AOT. | `RouteAdapterGenerator.java:104,110` vs `PathParamResolver.java:101` |
| 1.2 | **AOT vs runtime handler return-value divergence.** AOT emits `ctx.ok(result)` for non-void handlers; runtime discards `method.invoke()` return. Non-void controller that doesn't write ctx → 404 on runtime, works on AOT. | `RouteAdapterGenerator.java:149-159` vs `HandlerFactory.java:45` |
| 1.3 | **AOT vs runtime inherited-method divergence.** Runtime uses `clazz.getMethods()` (inherited), AOT uses Jandex `ci.methods()` (declared only). Inherited controller methods register differently per engine. | `RuntimeBeanAdapter.java:225` vs `BeanEnrichment` |
| 1.4 | **`BeanEnrichment` references non-existent `WebContext`.** `paramType.equals("...web.WebContext")` — class doesn't exist anywhere. Dead branch, string-coupling rotted. | `BeanEnrichment.java:244-245` |
| 1.5 | **Gin-contract check (first param must be HttpContext) only in Jandex path.** Runtime engine never enforces it. | `BeanEnrichment.java:151-166` vs `RuntimeBeanAdapter.collectRoutes` |
| 1.6 | **Core hardcodes demo-app class names.** `com.github.dropguard.summer.realworld.common.LimitOffsetPageable` and `com.github.dropguard.summer.twitter.common.CursorPageable` in framework core. | `BeanEnrichment.java:195-214` |

## 2. High — Architecture & Module Dependencies

| # | Issue | Location |
|---|-------|----------|
| 2.2 | **Infrastructure → Web + Data: `summer-aot-engine` depends on `summer-web` + `summer-data-jdbc` (compile).** AOT codegen hardcodes web/JDBC packages. *Resolution (2026-08-06, closed):* `summer-web` removed (pure `DotName`/`ClassName` strings, no type usage). `summer-data-jdbc` **kept as a compile dependency — verified genuine runtime type dependency**, not classpath supply: `WireMethodGenerator.emitRowMapperRegistrations` → `RowMapperFactory.scanJandex(index)` fires on every AOT build (unconditional call from `AotContextGenerator.generate`), and `TypeReads.jdbcRead` → `RowMapperFactory.resolveFieldType` runs inside aot-engine's own JVM, so a javac classpath channel (the proposed `withCompileClasspath`) cannot remove it. The proposal was evaluated and **discarded as superseded**: the "explicit classpath" goal it aimed for already exists via `SummerMojo` compiling generated sources with `project.getArtifacts()` (never `AotEngine.compile`/`resolveClasspath`, whose URLClassLoader fallback is now test-path-only), and the remaining alternatives (provided scope → plugin `NoClassDefFoundError`; reflection → duplicates the JDBC mapping contract, conflicts with §6; ServiceLoader extension → requires loading app jars in the plugin JVM, breaks isolation) all cost more than the dep. *Residual mitigation:* lazy gate added — `emitRowMapperRegistrations` returns early unless the Jandex index actually contains `@RowModel` classes (pure-`DotName` lookup, data-jdbc-free), so non-JDBC apps never load `RowMapperFactory`/HikariCP during AOT codegen. | `summer-aot-engine/pom.xml`, `WireMethodGenerator.java` |

## 3. High — SPI & API Design Issues

| # | Issue | Location |
|---|-------|----------|
| — | *(all items resolved — see Resolved table)* | |

## 4. Medium — Public API Design Issues

| # | Issue | Location |
|---|-------|----------|
| 4.11 | **`Request.getAttributes()`/`getHeaders()` return internal mutable maps.** | `Request.java:74-76, 137-139` |

## 5. Medium — Code Quality & Dead Code

| # | Issue | Location |
|---|-------|----------|
| 5.1 | **`Builder.removeByInstance(Object)` — zero callers.** | `BeanContainer.java:246-249` |
| 5.2 | **`BeanDefinition.isAutoCloseable` — never read or written.** | `BeanDefinition.java:147` |
| 5.3 | **`DefaultPageRequest.from(Request)` — zero callers, duplicates `DefaultPageResolver.resolve`.** | `DefaultPageRequest.java:383-398` |
| 5.4 | **`AotKey.forUniverse()` — zero callers.** | `AotKey.java:595-597` |
| 5.5 | **`HttpStatus.fromCode(int)` — zero callers.** | Deleted (already absent; 4.6) |
| 5.6 | **`AotContextGenerator.generate(List, MockedBean[])` and `buildJavaFile(List, MockedBean[])` overloads dead.** | `AotContextGenerator.java:71-73, 108-110` |
| 5.7 | **`SharedDependencyResolver.dfs()` `inStack` added/removed but never read — claimed cycle detection is dead scaffolding.** | `SharedDependencyResolver.java:170-189` |
| 5.8 | **`SharedDependencyResolver.resolve(beans, Set)` / `SharedConditionEvaluator` unused overloads.** | `SharedDependencyResolver.java:60`, `SharedConditionEvaluator.java:56,79` |
| 5.9 | **`AotEngine` javadoc claims container cache that doesn't exist in this class.** | `AotEngine.java:22-25, 57-60` |
| 5.10 | **Stale javadoc references to non-existent types.** `TargetInvoker` → `DefaultInvocationContext`, `ConfigBinder` → `core.util.TypeConverter` (actual: `core.config`), `ThrowableResolver` → `summer.validation.BodyValidator`, `WsRouteProvider` → `RouteProvider`, `AotEngine` → `summer.test.Testing`. | Various |
| 5.11 | **`Criteria.java` uses tab indentation — only file in codebase.** | `Criteria.java` |

## 6. Medium — Duplicated Logic

| # | Issue | Location |
|---|-------|----------|
| 6.1 | **`combinePaths` implemented 3 times.** | `PathUtils.java:30-48`, `BeanEnrichment.java:259-265`, `RuntimeBeanAdapter.java:349-359` |
| 6.2 | **`collectJavaFiles` duplicated verbatim.** | `AotEngine.java:240-250`, `SummerMojo.java:247-254` |
| 6.3 | **AOP binding discovery duplicated 3 times.** | `BeanEnrichment.detectAopBindings`, `RuntimeBeanAdapter.collectAopBindings`, `AotProxyGenerator.generate` |
| 6.4 | **`@WithDefault` extraction 3 implementations: Jandex (Discovery), reflection (RuntimeConfigBinder), Jandex-AOT (WireMethodGenerator).** | 3 files |
| 6.5 | **ScrollRequest/pageable detection 3 implementations with different matching logic.** | `BeanEnrichment`, `RuntimeBeanAdapter`, `RuntimeHandlerParam` |
| 6.6 | **`BeanContainer.getBean`/`getBeans` logic copied verbatim between Container and Builder.** | `BeanContainer.java:64-97, 197-227` |
| 6.7 | **`@Mock` scanning duplicated: `SummerTestLifecycle.createMocks` and `AotKey.mockedTypes`.** | 2 files |
| 6.8 | **Two parallel proxy implementations: runtime `ProxyFactory` + AOT `AotProxyGenerator`.** | 2 files |
| 6.9 | **Two parallel bean-metadata extractors: Jandex `BeanEnrichment` (422 lines) + reflective `RuntimeBeanAdapter` (387 lines).** | 2 files |
| 6.10 | **`List<T>` generic handling 4 implementations with subtle differences.** | `BeanEnrichment`, `RuntimeBeanAdapter`, `WireMethodGenerator` |
| 6.11 | **`RuntimeConfigBinder` re-creates own `SummerObjectMapper.createYaml()` — second YAML mapper in runtime module.** | `RuntimeConfigBinder.java:117` |
| 6.12 | **`RowMapperFactory` builds `new ObjectMapper().findAndRegisterModules()` instead of using `SummerObjectMapper`.** | `RowMapperFactory.java:443` |
| 6.13 | **`JsonBodyConverter` double-registers `JavaTimeModule` on top of `SummerObjectMapper.create`'s own.** | `JsonBodyConverter.java:507-519` |
| 6.14 | **`JsonBodyConverter` silently overrides JSON defaults: `INDENT_OUTPUT=true`, `ALWAYS` include nulls — no comment why.** | `JsonBodyConverter.java:505,521` |

## 7. Medium — Error Handling

| # | Issue | Location |
|---|-------|----------|
| 7.2 | **`OriginPolicy` `Integer.parseInt` on malformed host:port → uncaught `NumberFormatException`.** | `OriginPolicy.java:264-265` |
| 7.3 | **`GrpcServerRunner.stop()` with zero timeout still calls unbounded `server.awaitTermination()` — blocks indefinitely.** | `GrpcServerRunner.java:397-401` |
| 7.4 | **`NettyHttpServerHandler.exceptionCaught` wraps close-failure in `RuntimeException` — turns best-effort cleanup into throwing callback.** | `NettyHttpServerHandler.java:248-254` |
| 7.5 | **`SummerMojo` silently drops JARs with corrupt Jandex indexes (`catch Exception ignored`).** | `SummerMojo.java:290-291` |
| 7.6 | **`HandlerFactory` wraps web handler failure in `SummerAopException` (error code 5001) — wrong exception family.** | `HandlerFactory.java:50-54` |
| 7.7 | **`RuntimeBeanAdapter.findSinglePublicConstructor` logs warning and returns null instead of throwing like `BeanEnrichment`.** | `RuntimeBeanAdapter.java:160-170` |
| 7.8 | **`AotEngine.compile` wraps all failures in bare `RuntimeException`, discarding typed exception taxonomy.** | `AotEngine.java:177-186` |

## 8. Low — Visibility & @Internal Gaps

| # | Issue | Location |
|---|-------|----------|
| 8.1 | **`NettyHttpServer.create` is `public static` in a package-private class — `public` meaningless.** | `NettyHttpServer.java:48` |
| 8.2 | **`RequestContextHolder` — static `ThreadLocal` global state, public, no @Internal.** | `RequestContextHolder.java:25` |
| 8.3 | **`TransactionInterceptor` — static `ThreadLocal`, public static `isInterceptorActive()`.** | `TransactionInterceptor.java:21-22` |
| 8.4 | **`ThreadLocalTransactionContext` — static `ThreadLocal<Connection>`.** | `ThreadLocalTransactionContext.java:12` |
| 8.5 | **`MetricsRegistry` uses `@Component` (framework code should use `@Configuration`).** | `MetricsRegistry.java:12` |
| 8.6 | **Test infrastructure in production jar: `TestClassIndexer` in `summer-runtime/main`.** | `summer-runtime/src/main/.../TestClassIndexer.java` |
| 8.7 | **@Internal annotation gaps.** `ApplicationState`, `RouterRegistry`, `RequestContextHolder`, `HttpParameterResolverChain`, `QueryBuilder`, `Criteria`, `MutationBuilder`, `NettyServerRunner`, `NettyServerConfiguration`, `NettyWebSocketBroadcaster` — public framework-internal classes without `@Internal`. | Various |
| 8.8 | **`BeanEnrichment` public with Jandex in constructor signature — internal implementation detail on public type.** | `BeanEnrichment.java` |

## 9. Low — Inconsistency

| # | Issue | Location |
|---|-------|----------|
| 9.1 | **`SharedDependencyResolver` Kahn's algorithm uses `!sorted.contains()` per edge — O(n²).** | `SharedDependencyResolver.java:311-318` |
| 9.2 | **Fully-qualified exception names inlined instead of imports.** `Discovery`, `BeanEnrichment`, `ContainerEngines`, `RuntimeBeanAdapter`. | Various |
| 9.3 | **`MetricsRegistry` uses `@Component` while siblings `CorsMiddleware`/`LoggingMiddleware`/`MetricsMiddleware` are annotation-free — three styles in one module.** | `summer-web-middleware` |
| 9.4 | **`WireMethodGenerator` — 683 line god generator with 5+ responsibilities.** | `WireMethodGenerator.java` |
| 9.5 | **`SummerApplication` shutdown hook nests 4 levels deep.** | `SummerApplication.java:64-114` |

## 10. Resolved This Session

| # | Issue | Fix |
|---|-------|-----|
| — | `@Component.value()` dead attribute | Deleted |
| — | 1.1 AOT vs runtime PATH/QUERY param binding-key divergence | `RouteAdapterGenerator` uses `param.bindingName` (matches runtime) |
| — | 1.2 AOT vs runtime handler return-value divergence | AOT no longer auto-emits `ctx.ok(result)` — matches runtime (discard) |
| — | 1.4 BeanEnrichment references non-existent `WebContext` | Dead branch removed with the 2.5 migration (route scanning left BeanEnrichment entirely) |
| — | Gin-contract: controller methods must return void | Enforced at build time in both BeanEnrichment and RuntimeBeanAdapter |
| — | 1.5 Gin-contract check only in Jandex path | Now enforced in both paths |
| — | 2.1 `summer-runtime` depends on `summer-web` (compile) | New `summer-runtime-web` module absorbs the 6 web bridge classes (runtime-web package); `summer-runtime` is now a pure DI engine with zero `summer.web` references; ArchUnit regression rule enforces it |
| — | 2.5 Core hardcodes web/AOP class names as strings | Route scanning moved to `runtime.web.WebRouteScanner` (reflection, lives in `summer-runtime-web` so the web module stays reflection-free), activated via `META-INF/services`; shared collector `core.spi.RouteRegistrarLoader` consumed by both DI engines; core no longer knows web annotations; `VALIDATED_BODY` collapses to `BODY` + `validated=true` |
| — | 3.14/9.6 `ContainerEngine` SPI carries AOT-specific `cacheKey`/`className` | `build()` reduced to engine-agnostic (deployment, mocks, overrides); AOT codegen params moved onto `BeanDeployment.withCodegen(cacheKey, className)`; `RuntimeContainer` no longer silently ignores SPI params |
| — | 2.3 Core depends on Jandex; Jandex leaks into public SPI | New `summer-engine` module absorbs the Jandex-bearing types (`Discovery`, `BeanEnrichment`, `BeanDeployment`) plus the engine SPI (`ContainerEngine`/`ContainerEngines`) and `SharedConditionEvaluator`; `summer-core` is now Jandex-free with zero `org.jboss.jandex` imports |
| — | 1.3 AOT vs runtime inherited-method divergence | `RuntimeBeanAdapter` deleted; both engines now collect routes through the shared SPI scanner (`WebRouteScanner.getDeclaredMethods()`) — one code path, no per-engine divergence |
| — | 1.6 Core hardcodes demo-app class names (`LimitOffsetPageable`/`CursorPageable`) | Route scanning removed from core (2.5); no demo class names remain in `summer-core` |
| — | 2.4 Split package `web.websocket` | Implementations (`MapWsRouter`/`RadixWsRouter`) moved to the `web.websocket.router` sub-package in `summer-web-websocket`; the `web.websocket` package is now owned by exactly one jar (`summer-web`), JPMS-clean |
| — | 3.1 Public SPI depends on `@Internal` types | Mostly auto-resolved by the SPI refactor: `HandlerParam` no longer references `RouteInfo.ParamBinding`; web public layer has zero `@Internal`-typed API surface (verified). `ContainerEngine.build(BeanDeployment, MockedBean, Map)` is internally consistent — the engine SPI and its parameter types are all `@Internal` (internal SPI; third-party engines are not on the roadmap, so no external contract is promised) |
| — | 3.2 `@Order` javadoc contradicts implementation | Class-level javadoc already aligned ("beans without @Order sort last" = `MAX_VALUE`); residual trap was the `value()` javadoc "Default is 0" (only true when the annotation is present) — clarified: bare `@Order` sorts first (0), no annotation sorts last (`MAX_VALUE`) |
| — | 3.3 `@Order` is TYPE-only (cannot order `@Bean`-produced beans) | Accepted as a design limitation: the only use case (multiple same-type `@Bean` products into `List<T>`) sits at the edge of the ambiguity fail-fast model, has zero current users, and declaration order already provides fallback ordering. Full support would touch discovery + runtime + AOT generator for no users; revisit only if a real need appears |
| — | 3.4 Duplicate `ValidationException` simple names | Auto-resolved: `core.validation.ValidationException` no longer exists (superseded by `ConfigValidationException`); exactly one framework `ValidationException` remains (`web.exception`, consumed by demo `GlobalErrorHandler`s). No stale `core.validation.ValidationException` references anywhere |
| — | 3.5 `HttpContext` read facade exposes mutable internals | Two of three items already fixed: `headers()` returns `unmodifiableMap` (zero-copy wrapper), `statusCode()` renamed to `status()` (HttpStatus-typed). `body()` kept as a zero-copy internal-buffer reference with an explicit read-only javadoc contract (byte[] cannot be view-wrapped; IO layer wraps it via `Unpooled.wrappedBuffer`, middleware read-modify-write never mutates) — the "forward, don't copy" route, matching the middleware use pattern |
| — | 3.6 `Request.getQueryParameters()` re-parses / deprecated decoder / last-wins / swallowed errors | Caching and the Charset decoder were already in place; fixed the two real residuals: duplicate keys now first-wins (servlet `getParameter` convention) via `putIfAbsent`, and decode errors are caught narrowly (`IllegalArgumentException`) with a debug log + lenient raw fallback. Also fixed a pre-existing quirk: empty segments (`?a=1&&b=2`, leading `&`) no longer produce an empty-key entry. Added tests for all three behaviors |
| — | 3.7/7.1 `HttpMethod` missing PATCH/HEAD/TRACE → 500 instead of 405 | Already fixed: enum now has PATCH/HEAD/OPTIONS + `UNKNOWN` catch-all (javadoc: handler should return 405); `NettyRequestAdapter` maps unknown methods to `UNKNOWN` (no throw), `NettyHttpServerHandler` answers `405 METHOD_NOT_ALLOWED`. TRACE intentionally not added — unknown methods are rejected with 405 |
| — | 3.8 `HttpContext` per-request `Validator.builder().build()` | Already fixed: the Validator is a `static final` singleton initialized once at class load (the only `builder().build()` in the module); per-request allocation is limited to the lightweight `BodyParser` wrapper |
| — | 3.9 `@Transactional` has zero attributes | Accepted as intentional design (matches AGENTS.md "REQUIRED-only transactions"): minimal model — REQUIRED-only, any exception (checked or unchecked) rolls back; `propagation`/`readOnly` deliberately absent. javadoc now states the contract explicitly |
| — | 3.10 `@ExceptionHandler` no catch-all / no ordering / silent overwrite | Mostly fixed by design: duplicate registration now throws `ExceptionHandlerConflictException` (no silent overwrite); catch-all exists implicitly via inheritance-chain lookup in `getHandler` (subclass exceptions match ancestor handlers, most specific wins); single mandatory value kept as the precise-binding complement. Added tests locking both behaviors |
| — | 3.11 `InterceptorChain` exposes no way to reach `java.lang.reflect.Method` | Resolved by design (javadoc now states it explicitly): `InterceptedMethod` is a deliberate zero-reflection view (name + binding annotations only) — the AOT engine emits these as compile-time constants, so a `Method` handle would break dual-engine parity (the reason it replaced `MethodMetadata`). Annotation-member access is delegated to reflecting on the target method from the invocation context |
| — | 3.12 `AuthMiddleware.apply` undocumented `RequestContextHolder` side effect | The side effect is intentional (publishes the authenticated user via static ThreadLocal for services/AOP interceptors) and was only undocumented in the javadoc; `apply`'s javadoc now states it explicitly alongside the failure behavior |
| — | 3.13 `ApplicationRunner.run(BeanContainer) throws Exception` | Accepted as intentional design — identical to Spring's `ApplicationRunner`; startup-hook failure is a serious condition and the checked declaration makes it visible (both implementors genuinely fail: port binding, channel sync). Removing it would force silent wrapping and lose the compile-time failure signal |
| — | 3.15 `HttpParameterResolver.compile(HandlerParam)` perf hook in SPI | Accepted as a deliberate optimization extension point: it is a `default` method (zero burden on third-party resolvers, delegates to `resolve`), actively overridden by all 5 built-in resolvers (live code, not dead API), used only by the runtime engine's `HandlerFactory` — the AOT engine never invokes resolvers (generates inline handlers), so no dual-engine divergence |
| — | 3.16 Config-source inconsistency (`-Dsummer.engine`, gRPC system property, `SUMMER_DEV_PORT`) | gRPC port system-property channel removed — port now comes solely from `GrpcServerConfig` (@ConfigMapping, `${VAR}` placeholders cover externalization). `-Dsummer.engine` kept as a bootstrap parameter (must work before config binding selects the engine — same role as Quarkus bootstrap props). `SUMMER_DEV_PORT` kept as a dev-tool-only channel (dev-proxy redirect injected by the maven plugin); both remaining channels are now documented |
| — | 4.1 `SummerApplication.run(args)` ignores args / `throws Exception` / static SLF4J bridge / banner via System.out | `throws Exception` already resolved (`start()` wraps into `RuntimeException`); banner via System.out is intentional (startup display, AGENTS.md carve-out). Fixed: SLF4J bridge moved from the static initializer into `start()` (explicit, no class-load side effect). `args` accepted as intentional — Summer is configuration-driven (yml + `${VAR}`/`-D`), args are not a config channel; javadoc now states this |
| — | 4.2/9.7 `SummerBootstrap` dead interface with false javadoc | Deleted — zero references anywhere; the AOT path goes through `DiEngine.loadCompiledEngine` (reflective static `build()`), the interface was a phantom contract |
| — | 4.3 `JdbcTemplate.registerMapper` public but marked "not public API" | Marked `@Internal` (stays `public` because AOT-generated wire code calls it cross-package from `aot.generated`); javadoc now states the engine-contract role explicitly |
| — | 4.4 `JdbcTemplate` no `queryForList` with `RowMapper` | Added `queryForList(String, RowMapper, Object...)` and symmetric `queryForObject(String, RowMapper, Object...)` — custom mappings (joins, DTO projections) no longer require `registerMapper`; mapper resolution extracted to `resolveMapper`. Tests added |
| — | 4.5 `Request` accessor naming inconsistency + value-based `equals` on mutable object | Deleted `equals`/`hashCode` (zero consumers; value-semantics on a mutable object is an anti-pattern — identity is correct). Removed the duplicate `getQueryParameter(name)` accessor — `queryParam(name)` is the single parameterized-lookup API (paired with `pathParam`); naming rule is now: bare-name lookups (`pathParam`/`queryParam`) vs `getX()` field/collection access |
| — | 4.6 `HttpStatus` missing codes / `fromCode()` returns null | `fromCode` already absent (audit stale — no definition, no callers). Doubled the enum to 42 codes covering the common RFC 9110 responses (406/408/410/412/413/414/415/416/417/422/425/426/428/431/451/505/507/508/511 added; 422/429 were already present) |
| — | 4.7 WebSocket naming inconsistency (`WsInterceptor`/`WsFilterChain` vs `WebSocket*`) | Renamed to `WebSocketInterceptor` / `WebSocketInterceptorChain` (the `WebSocket*` family); servlet-style `doFilter` renamed to `proceed` (AOP chain idiom — the framework's single chain vocabulary); javadoc now states the text-only design boundary explicitly (binary frames never reach handlers/interceptors — the pipeline is `TextWebSocketFrame`-only by design) and that interception is before-only (short-circuit by not calling `proceed`). No behavior change; `WebSocketInterceptorIntegrationTest` green |
| — | DiEngine engine resolution consolidated | `Engine.fromString` is the single case-insensitive source of truth for legal engine names (blank treated as unset, unknown values throw `ConfigurationException` steering to the two legal values); `DiEngine.resolveEngine` applies the `-Dsummer.engine` override on top; `DiEngineEngineResolutionTest` locks all five behaviors |
| — | 4.8 `@RestController.value()` / method-route `value()` semantics undocumented | javadoc now states the full contract: `@RestController.value()` is an optional base path prefix (empty = no prefix); method-level `value()` is relative to the base path, empty = "use the base path itself", non-empty = appended (stand-alone when the controller has no base path); normalization via `PathUtils` documented. Applied to `RestController`, `Get`, `Post`, `Put`, `Delete` |
| — | 4.9 `BeanContainer` "Immutable" claim vs internal exposure + non-idempotent `close()` | Two of three items already fixed (audit stale): `Builder.routes()` snapshots via `List.copyOf`, and every `RouteInfo`/`ParamInfo` field is now `final` (constructor-injected). Fixed the real residuals: `close()` is now idempotent (synchronized `closed` guard — duplicate call is a logged no-op; `ShutdownContext.runAll` was already self-clearing); the singleton map was already defensively copied in the container constructor (`unmodifiableMap(new HashMap<>(...))`, verified — no builder leak). `RouteInfo.params` documented as construction-time metadata, read-only post-build. New `BeanContainerTest` locks all three contracts |
| — | 4.10 `Request.getRawPathBytes()` exposes radix-trie internals | Marked `@Internal` — an engine-level optimization channel for `RadixTreeHttpRouter` (cross-module, so it stays `public`; same pattern as `JdbcTemplate.registerMapper`); javadoc states the zero-allocation matching purpose, that the public surface is `path()`, and that the returned array must not be mutated |

## 11. Re-Verification After SPI Refactor (2026-08-06)

Every remaining open item (§4–§9) re-checked against the post-refactor tree (summer-engine / summer-runtime-web split, route scanning unified via `RouteRegistrarLoader` SPI, `RuntimeBeanAdapter`/`HandlerFactory`/`SummerBootstrap` removed). Verdicts: ✅ resolved (stale claim / auto-fixed by refactor), ✅ fixed (fixed this session, 2026-08-06), ⚠️ reduced or partial, ❌ still live. All fixes verified: `mvn test` green (757 tests).

| # | Audit claim | Verdict | Evidence |
|---|-------------|---------|----------|
| 4.11 | `Request.getAttributes()/getHeaders()` return mutable maps | ✅ resolved | audit line refs stale — `getHeaders()` no longer exists (removed in refactor); `getAttributes()` returns `Collections.unmodifiableMap` with read-only javadoc (`Request.java:136`); `HttpContext.headers()` likewise unmodifiable; no caller mutates the view |
| 5.1 | `Builder.removeByInstance` zero callers | ✅ resolved | method no longer exists |
| 5.2 | `BeanDefinition.isAutoCloseable` never read/written | ✅ resolved | field no longer exists |
| 5.3 | `DefaultPageRequest.from(Request)` zero callers | ✅ resolved | method no longer exists |
| 5.4 | `AotKey.forUniverse()` zero callers | ⚠️ stale | 2 real callers: `TestContainer.java:106,112` |
| 5.6 | `AotContextGenerator.generate(List,MockedBean[])` / `buildJavaFile(List,MockedBean[])` dead | ✅ resolved | dead overloads cleaned; `buildJavaFile` now private; both current `generate` overloads live (`SummerMojo:132`, `AotEngine:158`) |
| 5.7 | `SharedDependencyResolver.dfs()` `inStack` dead scaffolding | ✅ resolved | `dfs` gone; topo sort is `topologicalSort` (Kahn) |
| 5.8 | `SharedDependencyResolver.resolve(beans,Set)` / `SharedConditionEvaluator` unused overloads | ✅ fixed | Dead `evaluate(List,BeanDeployment)` + `evaluate(List,Set)` overloads deleted; the `moduleIndex` param was dead throughout (`resolveConditionalOnBean` never reads it — global visibility is deliberate) and is **removed from the API entirely**: `evaluate(beans)` / `evaluate(beans, mockedTypes)` only. `SharedDependencyResolver.resolve(List,Set)` kept — it is the shared impl behind the 1-arg API |
| 5.9 | `AotEngine` javadoc claims container cache not in class | ✅ fixed | javadoc rewritten: no cache exists — each distinct `cacheKey`/`className` compiles fresh (the JVM loads a class once, so keys prevent collisions, not dedupe); `Testing` ref corrected to the real SPI path (`AotContainer` via `ContainerEngines`) |
| 5.10 | Stale javadoc refs to non-existent types | ✅ fixed | All 5 corrected: `ConfigBinder` → `core.config.TypeConverter`; `ValidatingParameterResolver` → `ctx.validatedBody(...)` contract; `WsRouteProvider` → `{@link RouteRegistrar}`; `TargetInvoker` → "interception path"; `AotEngine` → `AotContainer` SPI |
| 5.11 | `Criteria.java` tab indentation | ✅ fixed | 134 tab lines converted to AOSP spaces; spotless-clean |
| 6.1 | `combinePaths` ×3 | ⚠️ reduced | 2 remain: `PathUtils.combinePaths` (public) + `WebRouteScanner.java:212` (private copy, could delegate) |
| 6.2 | `collectJavaFiles` duplicated verbatim | ✅ fixed | extracted to `aot.JavaSourceFiles.collect` — both `AotEngine` and `SummerMojo` delegate |
| 6.3 | AOP binding discovery ×3 | ⚠️ reduced | 2 remain: `BeanEnrichment.detectAopBindings` + `AotProxyGenerator.generate` scanning (L60-73); `RuntimeBeanAdapter` copy gone |
| 6.4 | `@WithDefault` extraction ×3 | ✅ fixed | `Discovery`'s copy was **dead code** — `extractDefaultValues` wrote `ConfigPropertiesBean.defaultValues`/`fieldTypes`, maps read by nobody (AOT re-scans the index; runtime reads the annotation reflectively). Deleted the method, both dead maps, and the now-unused `@WithName`/`resolveKeyName`/`normalizeKey` helpers. Remaining 2 (WireMethodGenerator Jandex-AOT + RuntimeConfigBinder runtime reflection) are dual-engine-inherent |
| 6.5 | pageable detection ×3 divergent | ✅ resolved | `RuntimeBeanAdapter`/`RuntimeHandlerParam` gone; matching unified in `DefaultPageResolver`/`CursorPageResolver` (runtime-web) with AOT deferring via `@Replaces` (`RouteAdapterGenerator.java:121`) |
| 6.6 | `BeanContainer.getBean/getBeans` copied Container↔Builder | ✅ fixed | single private static lookup core (`getBean(Map,Class)` / `getBeans(Map,Class)`), both views delegate (Builder's call is `BeanContainer.`-qualified — its own `getBean(Class)` shadows otherwise) |
| 6.7 | `@Mock` scanning ×2 | ✅ fixed | shared `MockedParams.scan(testClass)` — `SummerTestLifecycle.createMocks` and `AotKey.mockedTypes` both delegate; removes the divergence risk between the mocked set used at build time and the one hashed into the cache key |
| 6.8 | Dual proxy implementations | ⚠️ architectural | `ProxyFactory` (runtime JDK) + `AotProxyGenerator` (compile-time) — inherent to dual-engine design, not accidental duplication |
| 6.9 | Dual bean-metadata extractors | ✅ resolved | `RuntimeBeanAdapter` deleted; single `BeanEnrichment` in summer-engine |
| 6.10 | `List<T>` generic handling ×4 | ✅ fixed | the two Jandex extraction impls (BeanEnrichment + Discovery) unified into `JandexTypes.paramTypeName`; Discovery now also fails fast on nested generics (was silently producing `List<List<X>>`). The runtime (`BeanInstantiator`) and AOT (`WireMethodGenerator`) resolution mechanisms stay separate — different engines, different mechanisms |
| 6.11 | `RuntimeConfigBinder` re-creates YAML mapper | ✅ resolved | now uses `SummerObjectMapper.createYaml()` from `core.json` (single factory, one mapper in runtime) |
| 6.12 | `RowMapperFactory` builds own `ObjectMapper` | ✅ fixed | `RowMapperFactory.java:137` → `SummerObjectMapper.create()` (shared mapper; `FAIL_ON_UNKNOWN_PROPERTIES=false` also protects record conversion) |
| 6.13 | `JsonBodyConverter` double-registers `JavaTimeModule` | ✅ fixed | custom serializers moved onto a `SimpleModule`; `SummerObjectMapper`'s own `JavaTimeModule` registration is no longer duplicated |
| 6.14 | `JsonBodyConverter` silently overrides JSON defaults | ✅ fixed | comment added documenting the deliberate API-response defaults (pretty-print + explicit nulls) |
| 7.2 | `OriginPolicy` uncaught `NumberFormatException` | ✅ fixed | guarded — malformed origin port now throws `IllegalArgumentException` with the offending value |
| 7.3 | `GrpcServerRunner.stop()` zero timeout still blocks | ✅ fixed | zero timeout → `shutdownNow()` + bounded `awaitTermination()` (immediate stop, no block); non-zero drain timeout → timed wait, then force-terminate |
| 7.4 | `exceptionCaught` wraps close-failure in `RuntimeException` | ✅ fixed | close failure logged (`log.warn`), never propagated out of the exception path; `NettyHttpServerHandlerTest` updated to the new contract |
| 7.5 | `SummerMojo` silently drops corrupt Jandex indexes | ✅ fixed | `loadFromJar` warns with the jar name when a declared index fails to parse (non-jar artifacts still skipped silently) |
| 7.6 | `HandlerFactory` wraps handler failure in `SummerAopException` | ⚠️ partial | now rethrows `RuntimeException` unwrapped; only checked exceptions → `SummerAopException` (`HandlerFactory.java:48-53`, moved to summer-runtime-web) — family still debatable for web handlers |
| 7.7 | `RuntimeBeanAdapter.findSinglePublicConstructor` | ✅ resolved | class deleted |
| 7.8 | `AotEngine.compile` bare `RuntimeException` | ✅ fixed | new `AotCompilationException` (core.exception, `ErrorCode.CONFIG_AOT_COMPILATION_FAILED` 2007) — typed, catchable, consistent with the framework taxonomy |
| 8.1 | `NettyHttpServer.create` public in package-private class | ✅ fixed | `create()` → package-private static (only caller is same-package `NettyServerRunner`) |
| 8.2 | `RequestContextHolder` public, no @Internal | ✅ fixed | `@Internal` added |
| 8.3 | `TransactionInterceptor` static ThreadLocal + public accessor | ⚠️ partial | `@Internal` already present; `public static isInterceptorActive()` kept — ThreadLocal tx-context is by design (deferred) |
| 8.4 | `ThreadLocalTransactionContext` static ThreadLocal | ⚠️ partial | `@Internal` added (L10), moved to `summer-data-jdbc/.../tx/` — ThreadLocal by design for tx context |
| 8.5 | `MetricsRegistry` uses `@Component` | ✅ fixed | `@Component` removed — the whole `summer-web-middleware` module is annotation-free / app-registered (no production consumer of the registry as a discovered bean; only tests). javadoc states the contract and the `@Configuration` escape hatch |
| 8.6 | `TestClassIndexer` in production jar | ✅ fixed | moved to `summer-test` (`com.github.dropguard.summer.test`) — the only real caller is `TestContainer`; `summer-runtime`/`summer-engine` javadoc refs updated |
| 8.7 | @Internal gaps (10 classes) | ✅ partial-fixed | 7 marked `@Internal`: `ApplicationState`, `RouterRegistry`, `RequestContextHolder`, `HttpParameterResolverChain`, `NettyServerRunner`, `NettyServerConfiguration`, `NettyWebSocketBroadcaster`. **Audit claim corrected:** `QueryBuilder`/`Criteria`/`MutationBuilder` are **user-facing query DSL** (demo `IssueRepository` uses `QueryBuilder`/`Criteria`) — must NOT be `@Internal` |
| 8.8 | `BeanEnrichment` public with Jandex in constructor | ✅ fixed | marked `@Internal` (engine plumbing, not user API — used only by `Discovery`) |
| 9.1 | Kahn topo O(n²) `!sorted.contains()` per edge | ✅ fixed | `topologicalSort` rebuilt with a dependents map — O(V+E), no full scan per node, no `contains` (a dependent is queued exactly once) |
| 9.2 | FQN exception names inlined | ✅ fixed | imports added in `Discovery`, `BeanEnrichment` (both exceptions), `HandlerFactory` |
| 9.4 | `WireMethodGenerator` god generator (683 L) | ✅ fixed | split (AST-derived boundaries via tree-sitter): 712→281 lines. `WireMethodGenerator` is now a facade over `ConfigImplGenerator` (config-binding codegen, 344 L), `RowMapperEmitter` (row-mapper codegen, 132 L), and `AotTypeNames` (48 L). Bonus: `safeClassName` was copy-pasted in 4 generators — consolidated into `AotTypeNames`; dead `buildDefaultsLiteral` removed; `resolveKey` javadoc's stale `Discovery.resolveKeyName` ref dropped. Verified: `mvn test` green, spotless clean |
| 9.5 | `SummerApplication` shutdown hook 4-deep nesting | ⚠️ style | still nested (`SummerApplication.java:76`) |

## 12. 2026-08-06 提交前审计（3 子系统并行子代理）与处置

提交前对未提交重构 + 全部修复做了三路并行审计（web 路由 / DI 引擎核心 / AOT 代码生成+测试）。全部已修复并验证（`mvn test` 全绿）。

| # | 子系统 | 发现 | 处置 |
|---|--------|------|------|
| 12.1 | Web | **AOT @Pageable 代码生成必炸**：格式串 `$T.PAGEABLE` 对上新 RouteInfoHandlerParam 签名生成 `null.PAGEABLE` | ✅ fixed — 格式串改 `$L`（annotationType=null 与 RuntimeHandlerParam 一致）+ 新增 `RouteAdapterGeneratorTest` 回归 |
| 12.2 | Web | 同上更深一层：生成代码 `getBean(HttpParameterResolverChain.class)`，但链 config 被 `@ConditionalOnBean(RuntimeDiMarker)` 门控，AOT 宇宙无此 bean → 运行期 NoSuchBeanException | ✅ fixed — 去掉 `HttpParameterResolverConfiguration` 的 RuntimeDiMarker 门（纯 resolver 装配，两引擎共享，`@Replaces` 语义收敛） |
| 12.3 | DI | DiEngine 错误码管道死：runtime 缺失报 2005 而非 2006（2006 全程不可达） | ✅ fixed — invokeBuild 按调用方 code 重包 |
| 12.4 | DI | **@WithName 双引擎分歧**：AOT 认、Runtime 只认方法名（TCK fixture 恰好同名未暴露） | ✅ fixed — RuntimeConfigBinder 键解析镜像 ConfigImplGenerator.resolveKey |
| 12.5 | DI | **@WithDefault 集合类型分歧**：Runtime 把 "" 喂给 convertValue(List) 抛异常，AOT 发 List.of() | ✅ fixed — withDefaultValue 镜像 defaultExpr（List/Map → 空集合） |
| 12.6 | DI | RuntimeContainer 每次构建污染共享 BeanDeployment 蓝图（重复追加 synthetic） | ✅ fixed — addSyntheticBean 按类型幂等 |
| 12.7 | AOT | **List\<MockedType\> 分歧**：AOT 空解析 List 发 List.of()，Runtime 给 [mock] | ✅ fixed — 空解析 List 改发 `builder.getBeans($T.class)` 镜像 Runtime；测试更新 |
| 12.8 | AOT | **ApplicationRunner 双启动**：@DualEngine RUNTIME 腿命中缓存仍重跑 NettyServerRunner → 端口冲突 | ✅ fixed — acquireUniverse 返回 fresh 标志，仅新构建启动 runner |
| 12.9 | AOT | 死代码：2 参 generateWireMethod + 死 overrides 字段 | ✅ fixed — 删除（AotEngine 构造调用改 1 参） |
| 12.10 | Web | WebRouteScanner 硬契约（非 void / 首参非 ctx → 启动抛） | ✅ 决策保留 — fail-fast 是框架哲学（clarity-first），0.1.0 期收紧契约 |
| 12.11 | Web | ExceptionRegistry 重复注册改抛（旧 last-wins） | ✅ 决策保留 — 静默覆盖是隐性 bug 源 |
| 12.12 | Web | `HttpContext.statusCode()` → `status()` 破坏性改名 | ✅ 决策保留（0.1.0 期改名成本最低）；过期 javadoc 已修 |
| 12.13 | Web | 裸 @PathParam/@QueryParam bindingName 退化成 argN（-parameters 未开） | ✅ fixed — parent pom 开 `<parameters>true</parameters>`（代码本就写 `param.getName()`），MethodParameters 属性已生成 |
| 12.14 | DI | populateInterceptors 死路径（interceptorBindingAnnotations 恒空，从不生效） | ✅ fixed — 删除（BeanEnrichment Step 3 是真匹配路径，已核实） |
| 12.15 | DI | ContainerEngine SPI 标了 @Internal，与 CLAUDE.md"public SPI 不标 @Internal"矛盾 | ✅ fixed — 移除 @Internal |
| 12.16 | — | **待办（设计/中等改动，不阻塞提交）**：① @Order 的 List 注入 AOT 未排序（Runtime 经 ORDER_COMPARATOR 排序）——需把 @Order 元数据带进 BeanDefinition；② RouteRegistrarLoader SPI 丢 required/defaultValue；③ 扫描（getDeclaredMethods）与解析（getMethods）可见性不对称；④ 每请求重建中间件链（预分配无 IO）；⑤ @ConditionalOnBean 类级/方法级覆盖序；⑥ $$ConfigImpl 同名跨包碰撞 |

## 13. 2026-08-06 复审（修复本身）— DI 引擎 + 测试基础设施

复审本轮所有 DI/测试修复（10 审查点，8 OK 实测确认；错误码重包、@WithName/@WithDefault 收敛、DTO 传递、runner fresh 标志、AOP 装配完整性、Kahn 重写等价性全部验证）。

| # | 发现 | 处置 |
|---|------|------|
| 13.1 | `addSyntheticBean` 重复类型**静默丢弃**（幂等保护当前无路径触发，但掩蔽接线错误） | ✅ fixed — 改 fail-fast（抛 IllegalStateException） |
| 13.2 | @DualEngine 的 **AOT leg 仍启动 ApplicationRunner**（fresh=true），universe 含服务器时与 RUNTIME 容器端口冲突 | ⚠️ 待办 — 当前无 @DualEngine+服务器测试；修复方向：AOT leg 跳过 runner 或先关 RUNTIME 容器 |
| 13.3 | 实例级索引缓存的线程安全（HashMap 惰性初始化） | ✅ fixed — testIndexCache 改 ConcurrentHashMap，productionIndex 改 volatile + 双重检查 |
| 13.4 | 枚举 @WithDefault 双引擎不一致（AOT `Enum.valueOf(raw.toUpperCase())` vs Runtime 大小写敏感 convertValue） | ⚠️ 会话前既有 — 记入待办，不阻塞 |
| 13.5 | 测试 universe 的 IndexView synthetic 内容双引擎不一致（Runtime=生产+test 合并，AOT=仅生产） | ⚠️ 会话前既有 — 仅记录 |
| 13.6 | AOT/web 修复复审（7 点，代理三次 503 中断后主会话完成）：① 门控移除端到端成立——tck 依赖 runtime-web，@DualEngine/AOT leg 的 classpath 含其索引，全量测试通过即证明 wire() 正确生成 8 个 resolver bean + 链 bean；② PAGEABLE 生成代码正确（回归测试）；③ List\<MockedType\> 镜像与 BeanInstantiator 语义一致；④ 死重载删除无残留；⑤ 拆分逐字忠实；⑥ -parameters 无 argN 依赖；⑦ AotCompilationException 传播完整 | ✅ 全部验证 |
| 13.7 | @Replaces-on-resolver 无法覆盖内建 resolver：链总是先放 fresh 内建实例（validatingResolver() 等直接调用）再追加用户 resolver → 用户 @Replaces DefaultPageResolver/CursorPageResolver 永不生效 | ⚠️ 会话前既有、双引擎一致 — 记入待办（若用户真需要可换 resolver，链装配需改为消费替换后的 bean 列表） |
| 13.8 | 无 pageable-on-AOT 端到端 fixture（回归测试只查生成源码文本，不构建运行 AOT 容器） | ⚠️ 建议后续补 AOT 容器构建 + 链 bean 解析的 fixture |
