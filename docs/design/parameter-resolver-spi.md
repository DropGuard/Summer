# Parameter Resolver SPI — Design Document

## Problem Summary

| # | Problem | Current Location |
|---|---------|-----------------|
| 1 | **Dual-path duplication** — runtime uses `HttpParameterResolver` chain; AOT inlines code per binding type | `HttpParameterResolverChain` vs `RouteAdapterGenerator.generateHandlerBody()` |
| 2 | **God resolver** — `ReflectionParameterResolver` handles HttpContext, Request, @PathParam, @QueryParam, Throwable | `ReflectionParameterResolver.java:17-59` |
| 3 | **Duplicated handler creation** — `RuntimeRouteRegistrar.createHandler()` and `RuntimeExceptionHandlerRegistrar.createHandler()` are near-identical (20 lines each) | `RuntimeRouteRegistrar:69-89`, `RuntimeExceptionHandlerRegistrar:42-62` |
| 4 | **Non-extensible chain** — hardcoded `[Validating, Pageable, Reflection]` in `HttpParameterResolverConfiguration` | `HttpParameterResolverConfiguration:54-56` |
| 5 | **Marker bean gating** — all runtime configs require `@ConditionalOnBean(RuntimeDiMarker.class)` | All `@Configuration` classes in `summer-runtime` |

Additionally: **AOT bug** at `BeanDiscovery.java:318` — passes `PAGEABLE_DOT` instead of `PATH_PARAM_DOT` when extracting `@PathParam` binding names.

---

## 1. Core Types

All new shared types go in **`summer-web`** (no runtime dependency). Built-in resolver implementations stay in **`summer-runtime`**.

### 1.1 `ParameterInfo` — Portable Parameter Descriptor

Replaces `java.lang.reflect.Parameter` as the unit of parameter metadata. Works for both runtime (built from reflection) and AOT (built from Jandex metadata).

```java
package summer.web;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * Portable descriptor of a method parameter, carrying its type, name, and
 * annotation values. Built from {@link java.lang.reflect.Parameter} at runtime
 * or from compile-time metadata in AOT mode.
 *
 * <p>
 * Resolvers use this to decide whether they handle a parameter, without
 * depending on reflection APIs. This is the unifying type that lets the same
 * {@link ParameterResolver} implementations work in both runtime and AOT modes.
 * </p>
 */
public final class ParameterInfo {

    private final Class<?> type;
    private final String name;
    private final Map<String, String> annotationValues; // FQCN → value()

    private ParameterInfo(Class<?> type, String name, Map<String, String> annotationValues) {
        this.type = type;
        this.name = name;
        this.annotationValues = annotationValues;
    }

    /** Java type of the parameter. */
    public Class<?> type() { return type; }

    /** Java parameter name (or AOT-assigned name). */
    public String name() { return name; }

    /**
     * Returns the annotation's {@code value()} if present, or {@code ""} if the
     * annotation is present but has no {@code value()} method. Returns
     * {@code null} if the annotation is not present.
     */
    public String annotationValue(Class<? extends Annotation> annotationType) {
        return annotationValues.get(annotationType.getName());
    }

    /** Whether the parameter carries the given annotation. */
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return annotationValues.containsKey(annotationType.getName());
    }

    /**
     * Build from a runtime {@link Parameter}. Extracts annotation names and
     * their {@code value()} reflectively.
     */
    public static ParameterInfo from(Parameter param) {
        Map<String, String> annValues = new HashMap<>();
        for (Annotation ann : param.getAnnotations()) {
            String value = "";
            try {
                value = (String) ann.annotationType().getMethod("value").invoke(ann);
            } catch (NoSuchMethodException ignored) {
                // Annotation has no value() — presence is still recorded
            } catch (InvocationTargetException | IllegalAccessException ignored) {
            }
            annValues.put(ann.annotationType().getName(), value);
        }
        return new ParameterInfo(param.getType(), param.getName(), Map.copyOf(annValues));
    }

    /**
     * Build from AOT metadata. Callers construct the annotation map from
     * Jandex annotation data at compile time.
     */
    public static ParameterInfo of(Class<?> type, String name,
                                   Map<String, String> annotationValues) {
        return new ParameterInfo(type, name, Map.copyOf(annotationValues));
    }
}
```

### 1.2 `ParameterResolver` — The SPI

```java
package summer.web;

import java.util.Comparator;

/**
 * SPI for resolving method parameters from an HTTP request. Implementations
 * declare what they handle and how to resolve it.
 *
 * <p>
 * The framework auto-discovers all {@code ParameterResolver} beans and assembles
 * them into a {@link ParameterResolverChain}. Resolution order is controlled by
 * {@link #getOrder()} — lower values are checked first.
 * </p>
 *
 * <h3>Implementing a resolver</h3>
 * <pre>{@code
 * public class CurrentUserResolver implements ParameterResolver {
 *     @Override
 *     public boolean supports(ParameterInfo param) {
 *         return param.hasAnnotation(CurrentUser.class);
 *     }
 *
 *     @Override
 *     public Object resolve(HttpContext ctx, ParameterInfo param) {
 *         String token = ctx.request().header("Authorization");
 *         return authService.getUser(token);
 *     }
 * }
 * }</pre>
 *
 * <h3>Ordering</h3>
 * <p>
 * Built-in resolvers occupy orders 0–400. User resolvers should use orders
 * above 0 (e.g., 100, 200) to run after type-based resolution but before
 * the body-deserialization fallback. Use {@link #HIGHEST_PRECEDENCE} to
 * intercept before everything else, or {@link #LOWEST_PRECEDENCE} to run last.
 * </p>
 */
public interface ParameterResolver {

    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /**
     * Can this resolver handle the given parameter? Called at route-registration
     * time in AOT mode (once) and at request time in runtime mode (per request,
     * but the result is deterministic for a given parameter).
     */
    boolean supports(ParameterInfo parameter);

    /**
     * Resolve the parameter value from the HTTP context. Only called if
     * {@link #supports(ParameterInfo)} returned {@code true}.
     */
    Object resolve(HttpContext ctx, ParameterInfo parameter);

    /**
     * Resolution priority. Lower values are checked first. Default is 0.
     */
    default int getOrder() { return 0; }
}
```

### 1.3 `ParameterResolverChain` — Ordered Dispatcher

```java
package summer.web;

import java.util.Comparator;
import java.util.List;

/**
 * Immutable chain that resolves method parameters by iterating registered
 * {@link ParameterResolver}s in priority order.
 *
 * <p>
 * If no resolver claims a parameter, the chain falls back to
 * {@link HttpContext#body(Class)} (implicit body deserialization). This
 * fallback cannot be overridden — it is the framework's last resort.
 * </p>
 */
public final class ParameterResolverChain {

    private final List<ParameterResolver> resolvers;

    public ParameterResolverChain(List<ParameterResolver> resolvers) {
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(ParameterResolver::getOrder))
                .toList();
    }

    /**
     * Resolve a parameter. Returns the first resolver's result, or falls back
     * to body deserialization.
     */
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        for (ParameterResolver resolver : resolvers) {
            if (resolver.supports(parameter)) {
                return resolver.resolve(ctx, parameter);
            }
        }
        return ctx.body(parameter.type());
    }

    /** The resolvers in resolution order (unmodifiable). */
    public List<ParameterResolver> resolvers() { return resolvers; }
}
```

### 1.4 `HandlerFactory` — Eliminates Handler Creation Duplication

```java
package summer.web;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Creates {@link Handler}s from reflection-based controller methods. Used by
 * both {@code RuntimeRouteRegistrar} and
 * {@code RuntimeExceptionHandlerRegistrar} — eliminating the duplicated
 * {@code createHandler()} methods.
 */
public final class HandlerFactory {

    private HandlerFactory() {}

    /**
     * Create a Handler that resolves all parameters via the chain, then invokes
     * the method reflectively.
     */
    public static Handler fromMethod(ApplicationContext context, Class<?> clazz,
                                     Method method, ParameterResolverChain chain) {
        method.setAccessible(true);
        Parameter[] params = method.getParameters();
        Object instance = context.getBean(clazz);

        return ctx -> {
            Object[] args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = chain.resolve(ctx, ParameterInfo.from(params[i]));
            }
            try {
                return method.invoke(instance, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getTargetException();
                throw (cause instanceof RuntimeException re)
                        ? re : new summer.aop.SummerAopException(
                                "Handler invocation failed", cause);
            } catch (IllegalAccessException e) {
                throw new summer.aop.SummerAopException(
                        "Cannot access handler method", e);
            }
        };
    }
}
```

---

## 2. Built-In Resolvers (split from the god resolver)

All in **`summer-runtime`**. Each is a focused, testable, independently replaceable bean.

### 2.1 `TypeParameterResolver` — HttpContext and Request injection

```java
package summer.runtime;

import summer.web.HttpContext;
import summer.web.ParameterInfo;
import summer.web.ParameterResolver;
import summer.web.Request;

/**
 * Resolves parameters typed as {@link HttpContext} or {@link Request}.
 * Highest priority — type matches are checked before annotation matches.
 */
public class TypeParameterResolver implements ParameterResolver {

    @Override
    public boolean supports(ParameterInfo parameter) {
        Class<?> type = parameter.type();
        return type == HttpContext.class || type == Request.class;
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        return parameter.type() == HttpContext.class ? ctx : ctx.request();
    }

    @Override
    public int getOrder() { return HIGHEST_PRECEDENCE; }
}
```

### 2.2 `PathParamResolver`

```java
package summer.runtime;

import summer.web.ParameterInfo;
import summer.web.ParameterResolver;
import summer.web.annotation.PathParam;

/**
 * Resolves {@link PathParam @PathParam}-annotated parameters from URL path
 * segments.
 */
public class PathParamResolver implements ParameterResolver {

    @Override
    public boolean supports(ParameterInfo parameter) {
        return parameter.hasAnnotation(PathParam.class);
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        String name = parameter.annotationValue(PathParam.class);
        if (name == null || name.isEmpty()) {
            name = parameter.name();
        }
        return ctx.request().pathParam(name);
    }
}
```

### 2.3 `QueryParamResolver`

```java
package summer.runtime;

import summer.web.ParameterInfo;
import summer.web.ParameterResolver;
import summer.web.annotation.QueryParam;

/**
 * Resolves {@link QueryParam @QueryParam}-annotated parameters from the URL
 * query string. Uses {@link TypeConverter} for boxed-type conversion.
 */
public class QueryParamResolver implements ParameterResolver {

    @Override
    public boolean supports(ParameterInfo parameter) {
        return parameter.hasAnnotation(QueryParam.class);
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        String name = parameter.annotationValue(QueryParam.class);
        if (name == null || name.isEmpty()) {
            name = parameter.name();
        }
        String value = ctx.request().queryParam(name);
        return TypeConverter.convert(value, parameter.type());
    }
}
```

### 2.4 `ThrowableResolver` — Exception handler parameter injection

```java
package summer.runtime;

import summer.web.ParameterInfo;
import summer.web.ParameterResolver;

/**
 * Resolves {@link Throwable}-typed parameters for {@code @ExceptionHandler}
 * methods. Reads the "last_exception" request attribute set by the framework.
 */
public class ThrowableResolver implements ParameterResolver {

    @Override
    public boolean supports(ParameterInfo parameter) {
        return Throwable.class.isAssignableFrom(parameter.type());
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        return ctx.request().getAttribute("last_exception");
    }
}
```

### 2.5 `ValidatingParameterResolver` — unchanged except uses `ParameterInfo`

```java
package summer.runtime;

import jakarta.validation.Valid;
import summer.web.ParameterInfo;
import summer.web.ParameterResolver;

public class ValidatingParameterResolver implements ParameterResolver {

    @Override
    public boolean supports(ParameterInfo parameter) {
        return parameter.hasAnnotation(Valid.class);
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        return ctx.validatedBody(parameter.type());
    }

    @Override
    public int getOrder() { return 100; } // after types/annotations, before body fallback
}
```

### 2.6 `PageableResolver` — unchanged except uses `ParameterInfo`

```java
package summer.runtime;

import summer.web.ParameterInfo;
import summer.web.ParameterResolver;
import summer.web.Pageable;
import summer.web.PageRequest;
import summer.web.Sort;

public class PageableResolver implements ParameterResolver {

    private final int defaultPage;
    private final int defaultSize;

    public PageableResolver(int defaultPage, int defaultSize) {
        this.defaultPage = defaultPage;
        this.defaultSize = defaultSize;
    }

    @Override
    public boolean supports(ParameterInfo parameter) {
        return Pageable.class.isAssignableFrom(parameter.type());
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        int page = parseInt(ctx.queryParam("page"), defaultPage);
        int size = parseInt(ctx.queryParam("size"), defaultSize);
        Sort sort = parseSort(ctx);
        return PageRequest.of(page, size, sort);
    }

    @Override
    public int getOrder() { return 200; } // after Validating, before body fallback

    // ... parseInt, parseSort helpers unchanged
}
```

---

## 3. Configuration — Auto-Discovery replaces Hardcoded Chain

### 3.1 Runtime Configuration

```java
package summer.runtime;

import java.util.Comparator;
import java.util.List;
import summer.core.RuntimeDiMarker;
import summer.core.annotation.Bean;
import summer.core.annotation.ConditionalOnBean;
import summer.core.annotation.Configuration;
import summer.web.ParameterResolver;
import summer.web.ParameterResolverChain;

/**
 * Registers built-in resolvers and assembles the chain.
 *
 * <p>
 * The chain auto-discovers ALL {@link ParameterResolver} beans in the
 * context — including user-defined ones. No manual chain assembly needed.
 * </p>
 */
@Configuration
@ConditionalOnBean(RuntimeDiMarker.class)
public class ParameterResolverConfiguration {

    // ── Built-in resolvers ──────────────────────────────────────────

    @Bean
    public TypeParameterResolver typeResolver() {
        return new TypeParameterResolver();
    }

    @Bean
    public PathParamResolver pathParamResolver() {
        return new PathParamResolver();
    }

    @Bean
    public QueryParamResolver queryParamResolver() {
        return new QueryParamResolver();
    }

    @Bean
    public ThrowableResolver throwableResolver() {
        return new ThrowableResolver();
    }

    @Bean
    public ValidatingParameterResolver validatingResolver() {
        return new ValidatingParameterResolver();
    }

    @Bean
    public PageableResolver pageableResolver(PageableProperties props) {
        return new PageableResolver(props.getDefaultPage(), props.getDefaultSize());
    }

    // ── Chain: auto-discovers ALL ParameterResolver beans ───────────

    @Bean
    public ParameterResolverChain resolverChain(List<ParameterResolver> resolvers) {
        return new ParameterResolverChain(resolvers);
    }
}
```

The `List<ParameterResolver>` parameter triggers auto-injection of all beans implementing `ParameterResolver`. Users add custom resolvers by declaring a `@Bean` — they appear in the chain automatically.

### 3.2 Eliminating the Old Configuration Classes

The following files are **deleted** (replaced by `ParameterResolverConfiguration`):

| File | Replacement |
|------|-------------|
| `HttpParameterResolverConfiguration.java` | `ParameterResolverConfiguration` |
| `HttpParameterResolverChain.java` | `ParameterResolverChain` (in `summer-web`) |
| `HttpParameterResolver.java` | `ParameterResolver` (in `summer-web`) |

The following are **refactored in place** (same file, new SPI types):

| File | Change |
|------|--------|
| `ReflectionParameterResolver.java` | **Deleted** — split into `TypeParameterResolver`, `PathParamResolver`, `QueryParamResolver`, `ThrowableResolver` |
| `RuntimeRouteRegistrar.java` | `createHandler()` → `HandlerFactory.fromMethod(...)` |
| `RuntimeExceptionHandlerRegistrar.java` | `createHandler()` → `HandlerFactory.fromMethod(...)` |

---

## 4. Resolution Flow

### 4.1 Runtime (Reflection) Path

```
Request arrives
    → router dispatches to Handler
    → Handler calls: resolverChain.resolve(ctx, ParameterInfo.from(param))
    → Chain iterates resolvers by order:
        1. TypeParameterResolver (MIN_VALUE) — HttpContext/Request
        2. PathParamResolver (0)             — @PathParam
        3. QueryParamResolver (0)            — @QueryParam
        4. ThrowableResolver (0)             — Throwable
        5. ValidatingParameterResolver (100) — @Valid
        6. PageableResolver (200)            — Pageable
        7. [any user resolver at N]          — e.g. @CurrentUser
        8. fallback: ctx.body(type)          — implicit body binding
    → resolved Object returned to Handler
    → Handler calls method.invoke(instance, args)
```

### 4.2 AOT (Compile-Time) Path

```
Maven plugin runs (compile time):
    BeanDiscovery.collectRouteMetadata()
        → For each parameter, builds a ParameterInfo (via Jandex)
        → Stores in RouteInfo.ParamInfo (existing structure, now carries annotation map)

    RouteAdapterGenerator.generate()
        → For each parameter, emits a static ParameterInfo field
        → Emits: resolverChain.resolve(ctx, STATIC_PARAM_INFO)
        → No binding-type-specific codegen (no if/else on PATH/QUERY/BODY/PAGEABLE)

Generated code (runtime):
    public final class GeneratedAnnotationRouterAdapter implements RouteRegistrar {
        private final ParameterResolverChain chain;

        private static final ParameterInfo P_ID = ParameterInfo.of(
            String.class, "id",
            Map.of("summer.web.annotation.PathParam", "id"));

        private static final ParameterInfo P_BODY = ParameterInfo.of(
            User.class, "user",
            Map.of("jakarta.validation.Valid", ""));

        @Override
        public void registerControllers(HttpRouter.Builder builder,
                                        ApplicationContext context) {
            var ctrl = context.getBean(UserController.class);
            builder.get("/users/{id}", ctx -> {
                String id = (String) chain.resolve(ctx, P_ID);
                User user = (User) chain.resolve(ctx, P_BODY);
                return ctrl.getUser(ctx, id, user);
            });
        }
    }
```

Key difference from current AOT: **no binding-type-specific code generation**. The generator only emits `ParameterInfo` construction + `chain.resolve()` calls. All resolution logic lives in the resolver implementations, shared between runtime and AOT.

### 4.3 AOT Context Wiring

The generated `AotContext` must register resolver beans:

```java
// In GeneratedAotContext (auto-generated)
@Override
public <T> T getBean(Class<T> type) {
    if (type == TypeParameterResolver.class) return (T) new TypeParameterResolver();
    if (type == PathParamResolver.class) return (T) new PathParamResolver();
    if (type == QueryParamResolver.class) return (T) new QueryParamResolver();
    if (type == ThrowableResolver.class) return (T) new ThrowableResolver();
    if (type == ValidatingParameterResolver.class) return (T) new ValidatingParameterResolver();
    if (type == PageableResolver.class) return (T) new PageableResolver(0, 20);
    if (type == ParameterResolverChain.class) return (T) new ParameterResolverChain(List.of(
        new TypeParameterResolver(), new PathParamResolver(), new QueryParamResolver(),
        new ThrowableResolver(), new ValidatingParameterResolver(), new PageableResolver(0, 20)));
    // ... other beans
}
```

The `AotContextGenerator` must be updated to discover resolver beans (via Jandex index) and generate their creation + chain assembly. This replaces the current approach where AOT bypasses resolvers entirely.

---

## 5. Usage Example — Custom @CurrentUser Resolver

```java
// ── 1. Define the annotation ───────────────────────────────────
package com.myapp.annotation;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}

// ── 2. Implement the resolver ──────────────────────────────────
package com.myapp.auth;

import summer.web.HttpContext;
import summer.web.ParameterInfo;
import summer.web.ParameterResolver;

public class CurrentUserResolver implements ParameterResolver {

    private final AuthService authService;

    public CurrentUserResolver(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean supports(ParameterInfo parameter) {
        return parameter.hasAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolve(HttpContext ctx, ParameterInfo parameter) {
        String token = ctx.request().header("Authorization");
        if (token == null) {
            throw new UnauthorizedException("Missing Authorization header");
        }
        return authService.getUser(token);
    }

    @Override
    public int getOrder() { return 300; } // after built-in resolvers
}

// ── 3. Register it as a bean ───────────────────────────────────
package com.myapp.config;

import com.myapp.auth.AuthService;
import com.myapp.auth.CurrentUserResolver;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

@Configuration
public class AuthConfiguration {

    @Bean
    public CurrentUserResolver currentUserResolver(AuthService authService) {
        return new CurrentUserResolver(authService);
    }
}

// ── 4. Use it in a controller ──────────────────────────────────
package com.myapp.controller;

import com.myapp.annotation.CurrentUser;
import com.myapp.model.User;
import com.myapp.model.UserProfile;
import summer.web.HttpContext;
import summer.web.annotation.Get;
import summer.web.annotation.RestController;

@RestController("/api")
public class UserController {

    @Get("/profile")
    public UserProfile getProfile(HttpContext ctx, @CurrentUser User user) {
        return new UserProfile(user.name(), user.email());
    }
}
```

The resolver is auto-discovered, added to the chain, and works identically in runtime and AOT modes. No framework modification needed.

---

## 6. What It Hides

| Complexity | Hidden Where |
|-----------|-------------|
| Chain assembly from all resolver beans | `ParameterResolverChain` constructor + Spring auto-injection |
| Resolution priority ordering | `ParameterResolver.getOrder()` — chain sorts internally |
| `ParameterInfo` construction from reflection | `ParameterInfo.from(Parameter)` |
| `ParameterInfo` construction from Jandex metadata | `ParameterInfo.of(type, name, annotationMap)` — AOT generator |
| Handler creation (reflection + invoke + exception unwrapping) | `HandlerFactory.fromMethod()` — called by both registrars |
| AOT vs runtime differences | Same `ParameterResolver` implementations + same `ParameterResolverChain` |
| Annotation value extraction (`@PathParam("id")` → `"id"`) | `ParameterInfo.annotationValue()` |
| Type conversion (`String` → `Integer`) | `TypeConverter` — internal to `QueryParamResolver` |
| Body deserialization fallback | Chain's built-in fallback: `ctx.body(type)` when no resolver matches |

---

## 7. Trade-Offs

### Better

| Aspect | Before | After |
|--------|--------|-------|
| **Extensibility** | Zero — chain is hardcoded | Full — implement `ParameterResolver`, declare a `@Bean` |
| **Single resolution path** | Two completely separate systems (reflection chain vs AOT inline codegen) | One chain used by both runtime and AOT |
| **God resolver** | 1 class handles 5 concerns | 5 focused classes, each independently testable/replaceable |
| **Handler creation** | Copy-pasted 20-line method in 2 classes | Single `HandlerFactory.fromMethod()` |
| **AOT codegen complexity** | 4 binding types × specific codegen logic each | One pattern: static `ParameterInfo` + `chain.resolve()` |
| **BeanDiscovery:318 bug** | `PAGEABLE_DOT` passed instead of `PATH_PARAM_DOT` | Eliminated — `ParameterInfo.of()` builds annotations uniformly |
| **Testability** | Must mock entire `ReflectionParameterResolver` | Test individual resolvers with `ParameterInfo.of(type, name, Map.of(...))` |

### Worse

| Aspect | Before | After |
|--------|--------|-------|
| **AOT per-request overhead** | Direct method calls (`ctx.request().pathParam("id")`) — zero dispatch | Virtual dispatch through `supports()` + `resolve()` per parameter (~5 `supports()` checks in worst case) |
| **AOT object allocation** | None — inlined code | `ParameterInfo` per parameter (mitigated: static final fields, created once at class load) |
| **Type safety** | AOT generates typed code (`String id = ctx.request().pathParam(...)`) | All `resolve()` calls return `Object`, cast at call site |
| **Discoverability of body fallback** | Implicit in chain (documented) | Still implicit in chain, but now users might override with a catch-all resolver that breaks it |
| **Bean count** | 3 resolver beans + 1 chain | 6 resolver beans + 1 chain (5 small resolvers replace 1 god resolver) |

### Mitigations for the "Worse" items

1. **AOT overhead**: For most web apps, I/O dominates. The 5 `supports()` checks (~5ns total via type/annotation comparisons) are negligible vs network/database latency. Performance-critical paths can use custom `ParameterResolver` implementations with fast `supports()`.

2. **Object allocation**: `ParameterInfo` instances are static final fields — created once at class load, zero per-request allocation. `Map.of()` in `ParameterInfo.of()` is allocated once.

3. **Type safety**: The cast is safe by construction (resolver produces the right type for the parameter). A generic `resolve` method is standard for this pattern (see Spring's `HandlerMethodArgumentResolver`).

4. **Bean count**: The 5 resolvers are 20–30 lines each. They're easier to understand, test, and replace than a single 60-line god class with 5-way if/else.
