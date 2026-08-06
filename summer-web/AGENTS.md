# summer-web — HTTP/WebSocket Abstractions Layer

## OVERVIEW

Pure interface/abstraction module defining Summer's HTTP execution model: Router + Middleware chain + Handler + WebContext deferred-write pattern. No IO implementation lives here — that is in `summer-web-netty`, `summer-web-middleware`, `summer-web-websocket`.

## STRUCTURE

```
summer.web
├── annotation/       # @RestController, @Get, @Post, @Put, @Delete, @PathParam, @QueryParam, @ExceptionHandler
├── exception/        # SummerWebException (base, carries HttpStatus), RouteConflictException, BodyParseException, ValidationException, ArchitectureViolationException
├── health/           # HealthRouteRegistrar — /health/ready, /health/live
└── websocket/        # WsRouter WsRouter.Builder, WebSocketHandler, WebSocketContext, WebSocketBroadcaster, WebSocketInterceptor, WebSocketInterceptorChain
```

## WHERE TO LOOK

| Class/Interface | Purpose |
|---|---|
| `HttpRouter` + `Builder` | Immutable router interface. Builder supports `get/post/put/delete`, `group()` for path prefixes + scoped middleware, `mount()` for modules, `use()` for middleware. Path param normalization (`:param` → `{param}`). |
| `Middleware` | `@FunctionalInterface Handler apply(Handler)` — wraps handlers for cross-cutting concerns. |
| `GlobalMiddlewareChain` | Immutable record of global middleware class list, built at startup. |
| `Handler` | `@FunctionalInterface void handle(HttpContext ctx)` — deferred write pattern (return value ignored). |
| `HttpContext` | Request/response facade. Read side: `request()`, `path()`, `body()`, `header()`. Write side: `json()`, `ok()`, `text()`, `error()`, `status()`, `setHeader()`. Parses + validates bodies via `BodyParser`. |
| `Request` | Immutable. Carries method, path (raw bytes for zero-alloc routing), query, body, headers, attributes. Query param URL-decoded. |
| `Response` | (package-private) Holds status, body bytes, resultObject + BodyConverter (deferred serialization), headers. |
| `BodyConverter` | Interface for JSON/other format serialization. Implemented by `JsonBodyConverter` (Jackson). |
| `BodyParser` | Separates parsing from validation. Delegates to `BodyConverter` + Avaje `Validator`. |
| `RadixTrie<V>` | Generic byte-level radix tree for high-performance path matching. Supports `{param}`, `*`, `**` wildcards. Shared by HTTP + WS implementations. |
| `PathMatcher` | Regex-based path pattern compilation/matching. Fallback for complex patterns. |
| `PathUtils` | Path normalization (leading slash, collapse slashes, no trailing slash). |
| `RouterRegistry` | Strategy registry: maps `RouterType` (RADIX_TREE | MAP) to factory functions for HTTP + WS routers. |
| `RouterType` | Enum selecting RadixTree vs Map router backend. Configured via `server.router-type` in YAML. |
| `RouteRegistrar` | Interface for controller registration (reflection vs AOT). Dual-engine bridge. |
| `ExceptionRegistry` + `ExceptionHandlerRegistrar` | Global exception handler registry. Hierarchy-aware lookup (walks superclass chain). Dual-engine registration. |
| `ExceptionHandler` (`@interface`) | Class-level `@ExceptionHandler(SomeException.class)` on methods. |
| `ServerConfig` | `@ConfigurationProperties` record: port, timeouts, max body size, CORS origins, WebSocket frame size, router type. |
| `WebInfrastructureConfiguration` | `@Configuration` providing `JsonBodyConverter` + `HealthRouteRegistrar` beans. |
| `AuthMiddleware` | Interface for auth providers: `authenticate(HttpContext)` returns userId or throws. |
| `RequestAttributes` | Typed attribute keys (`USER_ID`, `LAST_EXCEPTION`) to eliminate magic strings. |
| `ScrollRequest` | Marker interface for cursor-paginated requests. |
| `Sort` | Sort parameters (Spring Data JPA style: `Sort.by("field").descending()`). |
| `WsRouter` + `Builder` | Immutable WebSocket router. Builder supports `ws()`, `bind()` with lifecycle callbacks, `mount()`. |
| `WebSocketHandler` | `@FunctionalInterface void handle(WebSocketContext ctx)` |
| `WebSocketContext` | Interface: `send()`, `onMessage()`, `sendJson()`, `close()`, `pathParam()`, `header()`. |
| `WebSocketBroadcaster` | Room-based pub/sub: `join(room, ctx)`, `leave(...)`, `broadcast(room, msg)`, `broadcastAll(msg)`. |
| `WebSocketInterceptor` + `WebSocketInterceptorChain` | WebSocket text-message interceptor chain (analogous to HTTP Middleware; binary frames are out of scope by design). |
| `HealthRouteRegistrar` | Registers `/health/ready` (503 during shutdown) and `/health/live` (always 200). |
| `SummerWebException` | Base exception carrying `HttpStatus` for automatic response mapping. Subtypes: `RouteConflictException`, `BodyParseException`, `ValidationException`, `ArchitectureViolationException`. |

## CONVENTIONS

- **Deferred write pattern**: Handlers return `void` and write responses via `ctx.ok()`, `ctx.json()`, `ctx.text()`. Return values are ignored. Netty IO thread flushes context content after handler completes.
- **Immutable router**: `HttpRouter.route()` is the only public method. Routes are built entirely through `Builder.build()`. Same for `WsRouter`.
- **Middleware wrapping order**: Last-registered middleware wraps closest to the handler. `use()` at top-level is global; inside `group()` is route-scoped.
- **Path normalization**: Express-style `:param` is normalized to `{param}` in the builder. All router engines consume `{param}` syntax.
- **Router type selectable**: `server.router-type: RADIX_TREE` (production, byte-level trie) or `MAP` (development, easier debugging).
- **Exception hierarchy carries HTTP status**: Every `SummerWebException` embeds an `HttpStatus` for automatic error response mapping.
- **Request body Record enforcement**: Request DTOs must be Java Records. Non-record classes trigger `ArchitectureViolationException`.
- **Typed request attributes**: Use `RequestAttributes.USER_ID` rather than string keys, via `ctx.request().setAttribute(RequestAttributes.USER_ID, val)`.
- **WebSocket lifecycle via builder**: Use `WsRouter.Builder.bind("/path", ws -> ws.onConnect(...).onMessage(...).onClose(...))` rather than implementing `WebSocketHandler` directly.

## ANTI-PATTERNS

- ~~Controllers modifying `Request` state~~ — `Request` is intentionally read-only (no public mutators). Use `Response` or `HttpContext` for output.
- ~~Returning values from `Handler.handle()`~~ — ignored by framework. Always write via `ctx` methods.
- ~~Passing `HttpContext` or `Request` to background threads~~ — not thread-safe. Extract data (strings, primitives) before crossing thread boundary.
- ~~Route path string concatenation/dispatch logic~~ — use `HttpRouter.Builder` DSL. Do not manually parse or split paths in controllers.
- ~~Catching and swallowing exception mapping~~ — use `@ExceptionHandler` or let propagate to framework. `HttpContext.error()` already logs.
- ~~Blocking IO in `Handler`/`Middleware` without virtual thread awareness~~ — virtual threads handle blocking well, but long CPU-bound work should be offloaded.
- ~~Reflective method calls for body parsing/attribute access~~ — `BodyParser`, `RequestAttributes`, and typed attribute keys exist to avoid reflection. Use them.
- ~~Direct `Response` field access~~ — `Response` is package-private. Always use `HttpContext` write facade.
