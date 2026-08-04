# Summer Framework — Code Audit

> Generated 2026-08-03, updated 2026-08-04.

---

## 1. Resolved (2026-08-04 session)

| # | Issue | Fix |
|---|-------|-----|
| — | ConfigMapping proxy equals bug | Object methods routed before arg guard (getDeclaringClass) |
| — | ConfigBinder static abuse | Instance-based, InterfaceBinder/ConfigMappingProxyBinder deleted |
| — | ConfigBinderInterfaceTest 2 failures | bindInterface reads @WithDefault directly from method annotation |
| — | ProxyFactory.equals violates equals contract | Object methods handled on proxy itself, not delegated to target |
| — | No HTTP idle/read timeout | IdleStateHandler + ReadTimeoutHandler in Netty pipeline; connectionTimeout→idleTimeout |
| — | gRPC exception interceptor leaks internals | SummerGrpcException carries Status directly, no raw message leak |
| — | @Internal coverage far below documented count | 65 @Internal + 25 package-private classes across all modules |
| — | TimelineService Redis unbounded growth | unfollow cleanup + fanOut hard cap (1000 timeline / 500 own tweets) |
| — | LoginRateLimiter memory leak | Periodic sweep on size threshold |
| — | BeanContainer.getBeans O(n²) | HashSet dedup (O(1)) + @Order sorting |
| — | Summer-boot missing summer-web compile dep | Added direct dependency |
| — | TCK negative fixture stale jandex.idx | Deleted stale index; module deliberately has no Jandex plugin |
| — | GrpcTestConfig leaked gRPC server into all @SummerTest | Removed @Bean BindableService registration |
| — | SummerTestLifecycle no List<T> support | Uses Constructor.getGenericParameterTypes() for ParameterizedType |
| — | @Order annotation introduced | TYPE-level, int value() default 0; unannotated beans sort last (MAX_VALUE) |
| — | bigV→influencer rename | FollowRepository, TimelineService, tests |
| — | fanOut logic: small authors wrote to unused user:{author}:tweets | Only influencers (≥5000 followers) write to own tweet cache |
| — | GrpcExceptionInterceptor ErrorCode coupling | SummerGrpcException standalone (no longer extends SummerException) |
| — | gRPC EchoServiceGrpc hand-written stub | Restored minimal stub; GrpcTestConfig simplified |

---

## 2. Open

### None — all resolved.

---

## 3. IT Test Status

| Module | Tests | Pass | Notes |
|--------|-------|------|-------|
| Twitter (unit) | 34 | 34 | All green |
| Twitter (IT) | 21 | 21 | All green |
| gRPC | 4 | 4 | Consolidated |
| Runtime + Core | 80 | 80 | All green (ConfigBinderInterfaceTest fixed) |
| TCK (DI) | 3 | 3 | OrderedInjectionTest — @DualEngine green |
| TCK (gRPC) | 6 | 6 | GrpcBehaviorTest green |
