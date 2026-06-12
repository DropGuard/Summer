# RFC: Test Isolation via @TestProfile

## Status: Phase 1 Complete

## Problem

Summer lacks test isolation. Test fixtures (`@Component` classes in `src/test/java`) are either:
- Silently ignored (not in Jandex index) — fragile, implicit
- Explicitly registered via `registerComponent()` — verbose, test-only API
- Contaminating other tests if accidentally discovered

Quarkus solved this with `@TestProfile`: a per-test-class mechanism that controls which beans participate in the CDI container. Summer should adopt the same pattern.

## Goal

Both Runtime and AOT engines must support `@TestProfile` (TCK consistency). Test isolation is a core DI engine capability, not a testing convenience.

## Design

### 1. Annotations

```java
// summer-test/src/main/java/summer/test/annotation/TestProfile.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestProfile {
    Class<? extends SummerTestProfile> value();
}
```

### 2. Profile Interface

```java
// summer-test/src/main/java/summer/test/SummerTestProfile.java
public interface SummerTestProfile {

    // Which @Component classes are active (empty = all discovered beans)
    default Set<Class<?>> getEnabledBeans() { return Set.of(); }

    // Config overrides (merged with application.yml, higher priority)
    default Map<String, String> getConfigOverrides() { return Map.of(); }

    // Bean replacements (profile-scoped @Replaces)
    default Map<Class<?>, Class<?>> getBeanReplacements() { return Map.of(); }

    // Disable @GlobalMiddleware for this profile
    default boolean disableGlobalMiddleware() { return false; }

    // Tags for filtering (e.g., run only "slow" profile tests)
    default Set<String> tags() { return Set.of(); }
}
```

### 3. Runtime Engine Integration

**`ComponentScanner`** — add profile awareness:

```java
public void scan(SummerTestProfile profile) {
    IndexView index = JandexIndexLoader.buildIndex();
    this.lastIndex = index;
    registerDiscoveredBeans(index);
    if (profile != null) {
        applyProfile(profile);
    }
}

private void applyProfile(SummerTestProfile profile) {
    Set<Class<?>> enabled = profile.getEnabledBeans();
    if (!enabled.isEmpty()) {
        componentClasses.retainAll(enabled);
    }
}
```

**`RuntimeApplicationContext`** — read `@TestProfile` from test class, apply config overrides and bean replacements during initialization.

### 4. AOT Engine Integration

**`SummerMojo`** — add `process-test-classes` phase execution:

1. Scan test classes for `@TestProfile` annotations
2. For each unique profile, run bean discovery with profile filtering
3. Generate profile-specific `AotContext` (e.g., `AotContext_MockDbProfile`)
4. Default `AotContext` (no profile) stays unchanged

**Test framework** — `@SummerTest` extension reads `@TestProfile`, selects the corresponding generated `AotContext`.

**Build time impact**: Each profile triggers a separate code generation pass. Profile count is typically small (single digits), so overhead is minimal.

### 5. TCK Integration

```java
// Both engines use the same profile
@SummerTest
@TestProfile(StandardProfile.class)
class RuntimeDiTest extends AbstractDependencyInjectionTCK { ... }

@SummerTest
@TestProfile(StandardProfile.class)
class AotDiTest extends AbstractDependencyInjectionTCK { ... }
```

Profile-specific tests verify isolation behavior on both engines:

```java
@SummerTest
@TestProfile(CircularDependencyProfile.class)
class RuntimeCircularDependencyTest extends AbstractCircularDependencyTCK { ... }

@SummerTest
@TestProfile(CircularDependencyProfile.class)
class AotCircularDependencyTest extends AbstractCircularDependencyTCK { ... }
```

### 6. registerComponent() Deprecation

Once `@TestProfile` is implemented:
- `registerComponent()` becomes unnecessary for isolated tests
- Keep it for backwards compatibility, mark `@Deprecated`
- Tests that need ad-hoc bean registration use profile's `getEnabledBeans()` instead

### 7. Migration Path

| Phase | Scope | Description |
|-------|-------|-------------|
| 1 | summer-test | Add `@TestProfile` annotation and `SummerTestProfile` interface |
| 2 | summer-runtime | `ComponentScanner` profile filtering, `RuntimeApplicationContext` profile support |
| 3 | summer-maven-plugin | AOT plugin reads `@TestProfile`, generates profile-specific `AotContext` |
| 4 | summer-tck | Migrate fixture-dependent tests to use `@TestProfile` |
| 5 | summer-runtime | Deprecate `registerComponent()` |

### 8. Open Questions

- **Profile caching**: Quarkus restarts the container when profile changes. Summer should do the same (group tests by profile to minimize restarts).
- **Profile inheritance**: Should profiles support inheritance? (e.g., `MockDbProfile extends StandardProfile`)
- **Multiple profiles per test**: Quarkus only supports one profile per test class. Should Summer support multiple?

## References

- Quarkus Testing Guide: https://quarkus.io/guides/getting-started-testing
- QuarkusTestProfile: https://github.com/quarkusio/quarkus/blob/main/test-framework/junit5/src/main/java/io/quarkus/test/junit/QuarkusTestProfile.java
