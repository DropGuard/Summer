# Summer Framework — Architecture & Code Quality Audit

Generated: 2026-06-08

---

## 🔴 CRITICAL

### C1: BeanDiscovery @Replaces dead code for duplicate @Bean return types

**File:** `summer-maven-plugin/src/main/java/summer/plugin/BeanDiscovery.java:134-136`

Duplicate else-if branch makes `@Replaces` replacement for duplicate `@Bean` return types unreachable. The fallback path that should replace an existing bean of the same type is dead code.

**Status:** TODO

---

### C2: TransactionInterceptor ignores propagation parameter

**File:** `summer-tx/src/main/java/summer/tx/TransactionInterceptor.java`

**Status:** BY DESIGN — complex propagation (REQUIRES_NEW, nested REQUIRED) intentionally unsupported. JDK dynamic proxy does not intercept `this.inner()` calls, so same-bean nesting does not trigger the interceptor. Cross-bean nesting is an unsupported use case per design principle "REQUIRED only".

---

### C3: SimpleJdbcTransactionManager has no suspend/resume

**File:** `summer-tx/src/main/java/summer/tx/SimpleJdbcTransactionManager.java`

`begin()` always throws on nested calls. No suspend/resume mechanism exists to support REQUIRES_NEW.

**Status:** BY DESIGN — see C2. REQUIRES_NEW is defined in `TransactionPropagation` enum but intentionally unsupported.


---

### C4: Redis SET+EXPIRE non-atomic

**File:** `summer-data-redis/src/main/java/summer/data/redis/SummerRedisTemplate.java`

`set(key, value, ttl)` performs SET then EXPIRE as two separate commands. If the process crashes between them, the key persists without expiry. Should use `SETEX` or Redis pipeline/transaction.

**Status:** TODO

---

### C5: WebSocket frame size check uses char count instead of byte count

**File:** `summer-web-netty/src/main/java/summer/web/server/SummerWebSocketFrameHandler.java`

Frame size validation compares `text.length()` (character count) against `maxFrameSize` (intended as bytes). Multi-byte characters (CJK, emoji) bypass the limit. Should use `text.getBytes(UTF_8).length` or `ByteBuf.readableBytes()`.

**Status:** TODO

---

## 🟡 WARNING

### W1: RecordUtils.isRecord() is dead code

**File:** `summer-core/src/main/java/summer/core/RecordUtils.java`

`isRecord()` wraps `Class.isRecord()` but is never called from any module. Can be removed.

**Status:** TODO

---

### W2: Duplicated type conversion logic

**Files:**
- `summer-runtime/src/main/java/summer/runtime/ConfigurationLoader.java` — `parseDefaultValue()`
- `summer-runtime/src/main/java/summer/runtime/ReflectionParameterResolver.java` — `convertValue()`

Both contain near-identical string-to-type conversion logic (int, long, double, boolean, enum). Should be extracted to a shared utility in summer-core.

**Status:** TODO

---

### W3: Request.getQueryParameters() re-parses on every call

**File:** `summer-web/src/main/java/summer/web/Request.java`

Each call to `getQueryParameters()` parses the query string into a new HashMap. Should cache the result on first access.

**Status:** TODO

---

### W4: ExceptionRegistry.getHandler() linear superclass traversal

**File:** `summer-web/src/main/java/summer/web/ExceptionRegistry.java`

`getHandler()` walks the exception superclass chain on every invocation. For high-throughput error paths, this is O(n) per unique exception type. Consider caching handler lookups.

**Status:** TODO

---

### W5: RadixTreeHttpRouter.register() is public but should be package-private

**File:** `summer-web-http/src/main/java/summer/web/http/RadixTreeHttpRouter.java`

`register()` is only called during router construction. Making it public exposes an internal mutation API. Should be package-private.

**Status:** TODO

---

### W6: Response has public mutable fields

**File:** `summer-web/src/main/java/summer/web/Response.java`

Package-private class with public mutable fields (`statusCode`, `headers`, `body`). No encapsulation. Acceptable for internal use but fragile if visibility changes.

**Status:** TODO

---

### W7: DependencyGraph.getGraph() returns mutable internal state

**File:** `summer-runtime/src/main/java/summer/runtime/DependencyGraph.java`

`getGraph()` returns the internal `Map` directly. Callers can corrupt the dependency graph. Should return `Collections.unmodifiableMap()` or a copy.

**Status:** TODO

---

### W8: NettyHttpServer.findOptionalBean() swallows all exceptions

**File:** `summer-web-netty/src/main/java/summer/web/server/NettyHttpServer.java`

Catches `Exception` including `RuntimeException`. Could mask configuration errors or bean creation failures. Should catch only `NoSuchBeanException` or similar.

**Status:** TODO

---

### W9: GrpcServerRunner.resolvePort() has no NumberFormatException handling

**File:** `summer-grpc/src/main/java/summer/grpc/server/GrpcServerRunner.java`

`Integer.parseInt()` on a system property without try-catch. Malformed port values cause unhandled exception at startup.

**Status:** TODO

---

### W10: SummerObjectMapper missing WRITE_DATES_AS_TIMESTAMPS=false

**File:** `summer-core/src/main/java/summer/core/json/SummerObjectMapper.java`

`create()` doesn't set `WRITE_DATES_AS_TIMESTAMPS=false`. `JsonBodyConverter` overrides this independently, creating inconsistent serialization behavior.

**Status:** TODO

---

### W11: ConfigurationBinder public methods should be package-private

**File:** `summer-core/src/main/java/summer/core/config/ConfigurationBinder.java`

`extractSection()` and `normalizeKeys()` are public but only used by `ConfigurationLoader` (in a different module, which is why they're public). Consider moving to a shared internal utility.

**Status:** TODO

---

### W12: ProxyInterceptorChain has mutable currentIndex

**File:** `summer-aop/src/main/java/summer/aop/ProxyInterceptorChain.java`

`currentIndex` is mutable state on the chain object. Current usage is safe (single-threaded per chain) but the pattern is fragile. Consider making state explicit via method parameters.

**Status:** TODO

---

### W13: summer-tck protobuf-java missing test scope

**File:** `summer-tck/pom.xml`

`protobuf-java 3.25.5` has no `<scope>test</scope>`. Defaults to compile scope in a purely test-scoped module. Also hardcodes version outside BOM.

**Status:** TODO

---

### W14: summer-core logback-classic at compile scope

**File:** `summer-core/pom.xml`

`logback-classic` + `jul-to-slf4j` at compile scope. Should be `provided` or `optional` — downstream users should choose their own SLF4J binding.

**Status:** TODO

---

## 🔵 TEST COVERAGE GAPS

| Module | Gap |
|--------|-----|
| `summer-aop` | Zero local tests (only covered indirectly via runtime/TCK) |
| `summer-web-middleware` | Zero tests |
| `summer-web-websocket` | Zero tests |
| `summer-boot` | Zero tests |
| `summer-data-jdbc` | Empty test directory (TCK has 32 tests as compensation) |
| `summer-core` | No tests for `RecordUtils`, `SummerObjectMapper`, `Engine`, `ErrorCode` |
| `summer-grpc` | 6 source files, only 1 integration test |

---

## 📦 DEPENDENCY ISSUES

| Module | Issue |
|--------|-------|
| `summer-runtime` | Compile dependency on `summer-web` couples DI runtime to web layer |
| `summer-core` | `logback-classic` should be provided/optional |
| `summer-web` | `avaje-validator` version hardcoded (already in BOM) |
| `summer-data-redis` | Explicit `jackson` deps redundant (transitive through summer-core) |
| `summer-boot` | Explicit `summer-aop` dependency redundant (transitive through summer-runtime) |
| `summer-tck` | `protobuf-java` version hardcoded outside BOM |
| `summer-dependencies` (BOM) | Missing `jakarta.validation-api` and `protobuf-java` entries |
