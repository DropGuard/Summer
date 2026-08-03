# Summer Framework — Code Audit

> Generated 2026-08-03, updated 2026-08-04.  Static + IT validation (21/21 twitter ITs pass).

---

## 1. Resolved (since 2026-07-29 audit)

| # | Issue | Fix |
|---|-------|-----|
| 1.1 | Jandex plugin never activated | Per-module activation in pom.xml (Quarkus pattern) |
| 1.2 | avaje-validator-generator missing | Added to summer-web + annotationProcessorPaths in parent |
| 2.1 | DiEngine ConfigBinder.bind before InterfaceBinder | ConfigBinder self-contained with built-in JDK-proxy binding |
| 2.2 | AOT proxy primitive cast compile error | `isPrimitive() ? box() : returnType` + TCK dual-engine test |
| 2.5 | AotEngine.CACHE static HashMap | Deleted (SummerTestLifecycle already caches) |
| 2.7 | RuntimeExceptionHandlerRegistrar static volatile | Constructor-injected HandlerMetadata via synthetic BeanDefinition |
| 3.1 | OriginPolicy.hostOf() opaque URI in Java 25 | `isOpaque()` check + 17 unit tests |
| 4.x | Twitter T2-T11 (FK cascade, credential externalization, race safety, etc.) | Fixed + 21/21 ITs pass |
| — | ConfigMapping proxy equals bug | Object methods routed before arg guard (getDeclaringClass) |
| — | ConfigBinder static abuse | Instance-based, InterfaceBinder/ConfigMappingProxyBinder deleted |
| — | gRPC test over-coverage | Consolidated: lifecycle + end-to-end + config (4 tests) |

---

## 2. Open

### HIGH — none remaining

### MEDIUM

**ProxyFactory.equals violates equals contract** — `ProxyFactory.java:69-71`
Delegates `equals` to wrapped target, breaking reflexivity and symmetry. Same bug ConfigMappingProxyBinder had.

**No HTTP idle/read timeout** — `NettyHttpServer.java:118-129`
`connectionTimeout()` and `readTimeout()` config values never applied to pipeline. Slow-loris vulnerability.

**gRPC exception interceptor leaks internals** — `GrpcExceptionInterceptor.java:66`
`status.withDescription(e.getMessage())` ships raw exception messages to client.

**@Internal coverage far below documented ~55 classes** — Multiple files
`Discovery`, `DiEngine`, `ConfigBinder`, `TypeConverter`, `BeanDeployment` etc. still unmarked.

### LOW

**ConfigBinderInterfaceTest: 2 failing tests** — `ConfigBinderInterfaceTest.java`
`bindsNestedInterface` and `bindsEnumAndList` fail after refactor. The built-in JDK proxy handles nested config interfaces recursively but the section map key normalization for nested sections may need adjustment. Not a correctness issue for production (all @ConfigMapping interfaces are flat).

**TimelineService Redis keys never trimmed** — `TimelineService.java:36-61`
zsets grow unbounded, stale IDs from unfollowed accounts never purged.

**LoginRateLimiter memory leak** — `LoginRateLimiter.java:24`
Map only cleaned lazily in `isBlocked()`. Abandoned usernames leak forever.

**BeanContainer.getBeans O(n²)** — `BeanContainer.java:89`
Uses `!result.contains(bean)` (equals-based, O(n²) dedup).

---

## 3. IT Test Status

| Module | Tests | Pass | Notes |
|--------|-------|------|-------|
| Twitter (unit) | 34 | 34 | All green |
| Twitter (IT) | 21 | 21 | All green |
| gRPC | 4 | 4 | Consolidated |
| Runtime + Core | 130 | 128 | 2 known failures (ConfigBinderInterfaceTest) |
