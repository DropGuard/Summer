# Design: Per-Module ModuleInitializer Generation

## Problem

The current AOT pipeline generates a monolithic `GeneratedAotContext` with a single
`wire()` method containing ALL beans across ALL modules. This works but has
limitations:

- No module-level encapsulation
- No ability to lazy-load or skip modules
- No clear ownership of which beans belong to which module
- Difficult to test individual modules in isolation

## Goal

Generate per-module `ModuleInitializer` implementations so that:

- Each module's beans are registered by a dedicated initializer
- `AppBootstrap` aggregates them in dependency order
- The existing `GeneratedAotContext` is kept as fallback for tests

## Architecture Overview

```
AppBootstrap.launch()
  ├── new SummerCoreModuleInitializer().register(builder)    ← framework
  ├── new SummerWebModuleInitializer().register(builder)     ← framework
  ├── new SummerTxModuleInitializer().register(builder)      ← framework
  ├── new SummerGrpcModuleInitializer().register(builder)    ← framework
  ├── new AppModuleInitializer().register(builder)           ← application
  └── new ValidationPhaseRunner(builder).run()               ← post-registration
```

## Design Decisions

### 1. Module Provenance: Track artifact→IndexView mapping

**Approach:** Modify `loadIndexes()` to return a `List<ModuleIndex>` instead of
just `CompositeIndex`. Each `ModuleIndex` carries the artifact coordinate and its
associated Jandex index.

```java
record ModuleIndex(String groupId, String artifactId, IndexView index) {}
```

A `Map<String, String>` mapping class FQN → module ID is built during discovery.
Module IDs are derived from artifact coordinates:

- `summer:summer-core` → `summer.core`
- `summer:summer-web` → `summer.web`
- Application module → `app`

**Why not package naming?** Package names don't reliably indicate module ownership
(e.g., `summer.fixtures.*` could be in any module).

### 2. Generation Approach: Generate in the application module

Only the application module configures the AOT plugin. Per-module initializers
are generated there because:

- We have full classpath access to all dependency modules' Jandex indexes
- No need to configure AOT plugin in each framework module
- Simpler build configuration

Each initializer is named `{ModuleId}ModuleInitializer` and placed in the
`summer.core.aot` package.

### 3. Backward Compatibility: Keep GeneratedAotContext

`GeneratedAotContext` remains as the "monolithic" fallback. It's still used by
AOT tests (`AotAopTest`, `AotConfigurationPropertiesTest` etc.) that directly
instantiate it.

The new per-module initializers are a separate code path used by `AppBootstrap`.

### 4. Cross-Module Dependency Resolution

Add `getBean(Class<?> type)` to `ApplicationContext.Builder` so module
initializers can look up beans registered by earlier initializers:

```java
class Builder {
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object bean = singletons.get(type);
        if (bean != null) return (T) bean;
        for (Object candidate : singletons.values()) {
            if (type.isInstance(candidate)) return type.cast(candidate);
        }
        return null;
    }
}
```

Within a module, beans are created in topological order (same as current wire()
method). Cross-module dependencies are resolved via `builder.getBean()`.

### 5. Wire Code Reuse

`WireMethodGenerator` is refactored to accept a `CodeEmissionTarget` that
abstracts the registration API:

- **MONOLITHIC** (current): `singletons.put(X.class, x)` — used by
  `GeneratedAotContext`
- **BUILDER** (new): `builder.registerSingleton(X.class, x)` — used by
  `ModuleInitializer.register()`

Cross-module dependencies use `builder.getBean(X.class)` instead of referencing
local variables from other modules.

### 6. Validation Phase

The validation phase (running all `Validator` beans) must run AFTER all modules
are registered. It moves from `wire()` to `AppBootstrap.launch()`:

```java
public static ApplicationContext launch() {
    var builder = ApplicationContext.builder();
    new SummerCoreModuleInitializer().register(builder);
    new SummerWebModuleInitializer().register(builder);
    // ... more initializers ...
    
    // Validation Phase
    for (Object bean : builder.getSingletons().values()) {
        if (bean instanceof Validator validator) {
            Object target = builder.getBean(validator.targetType());
            if (target != null) validator.validate(target);
        }
    }
    
    return builder.build();
}
```

## File Changes

### Phase 1: Core API changes

| File | Change |
|------|--------|
| `summer-core/.../ApplicationContext.java` | Add `getBean(Class<?>)` to Builder |
| `summer-core/.../ApplicationContext.java` | Add `getSingletons()` (already exists) |

### Phase 2: Plugin infrastructure

| File | Change |
|------|--------|
| `summer-maven-plugin/.../BeanDefinition.java` | Add `public String moduleId` field |
| `summer-maven-plugin/.../SummerMojo.java` | Return `List<ModuleIndex>` from `loadIndexes()` |
| `summer-maven-plugin/.../SummerMojo.java` | Build class→moduleId map during discovery |
| `summer-maven-plugin/.../BeanDiscovery.java` | Accept and propagate moduleId |
| `summer-maven-plugin/.../BeanEnrichment.java` | No change (moduleId already set) |

### Phase 3: Code generation

| File | Change |
|------|--------|
| `summer-maven-plugin/.../ModuleInitializerGenerator.java` | **NEW** — generates per-module initializer classes |
| `summer-maven-plugin/.../WireMethodGenerator.java` | Add `EmissionTarget` enum, refactor to support both modes |
| `summer-maven-plugin/.../AotContextGenerator.java` | Pass `EmissionTarget.MONOLITHIC` to WireMethodGenerator |
| `summer-maven-plugin/.../AppBootstrapGenerator.java` | Add validation phase after all initializers |

### Phase 4: SummerMojo orchestration

| File | Change |
|------|--------|
| `summer-maven-plugin/.../SummerMojo.java` | Add step 7.5: generate per-module initializers |
| `summer-maven-plugin/.../SummerMojo.java` | Update step 8: discover generated initializers + existing ones |

## Detailed Implementation

### `ApplicationContext.Builder.getBean()`

```java
// In ApplicationContext.java
class Builder {
    // ... existing fields ...
    
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        Object bean = singletons.get(type);
        if (bean != null) return (T) bean;
        for (Object candidate : singletons.values()) {
            if (type.isInstance(candidate)) return type.cast(candidate);
        }
        return null;
    }
}
```

### `BeanDefinition.moduleId`

```java
public sealed class BeanDefinition permits ComponentBean, FactoryBean, ConfigPropertiesBean {
    public final String qualifiedName;
    public final String simpleName;
    public String variableName;
    public String moduleId;  // NEW
    // ... rest unchanged ...
}
```

### `ModuleIndex` record (in SummerMojo)

```java
record ModuleIndex(String groupId, String artifactId, IndexView index) {
    String moduleId() {
        return groupId + "." + artifactId;
    }
}
```

### Modified `loadIndexes()`

```java
private List<ModuleIndex> loadModules() throws IOException {
    List<ModuleIndex> modules = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    
    // Current project's output
    loadModuleFromDirectory(outputDirectory, "app", "app", modules, seen);
    
    // Dependency JARs
    for (Object obj : project.getArtifacts()) {
        Artifact artifact = (Artifact) obj;
        File file = artifact.getFile();
        if (file == null || !file.exists()) continue;
        
        String moduleId = artifact.getGroupId() + "." + artifact.getArtifactId();
        if (file.isDirectory()) {
            loadModuleFromDirectory(file, artifact.getGroupId(), artifact.getArtifactId(), modules, seen);
        } else if (file.getName().endsWith(".jar")) {
            loadModuleFromJar(file, artifact.getGroupId(), artifact.getArtifactId(), modules, seen);
        }
    }
    return modules;
}
```

### `ModuleInitializerGenerator` (new file)

Generates per-module initializer classes. For each module:

```java
// Generated: summer.core.aot.SummerWebModuleInitializer
public final class SummerWebModuleInitializer implements ModuleInitializer {
    @Override
    public void register(ApplicationContext.Builder builder) {
        // Beans from summer-web module, in topological order
        
        // Cross-module dependency
        summer.core.BeanContainer ctx = builder.getBean(summer.core.BeanContainer.class);
        
        // Module's own beans
        summer.web.impl.RadixTreeHttpRouter router = new summer.web.impl.RadixTreeHttpRouter();
        builder.registerSingleton(summer.web.HttpRouter.class, router);
        
        // ... more beans ...
    }
}
```

The generator:

1. Groups beans by `moduleId`
2. For each module, sorts beans topologically within the module
3. Identifies cross-module dependencies (beans whose constructor params are from
   different modules)
4. Generates `builder.getBean()` calls for cross-module deps
5. Generates `builder.registerSingleton()` calls for each bean

### Modified `WireMethodGenerator`

Add emission target abstraction:

```java
enum EmissionTarget { MONOLITHIC, BUILDER }

final class WireMethodGenerator {
    private final EmissionTarget target;
    
    void generateWireMethod(MethodSpec.Builder wire, List<BeanDefinition> beans) {
        for (BeanDefinition bean : beans) {
            // Generate instantiation code (same for both targets)
            emitInstantiation(wire, bean);
            
            // Generate registration code (differs by target)
            if (target == EmissionTarget.MONOLITHIC) {
                wire.addStatement("singletons.put($T.class, $N)", beanClass, varName);
            } else {
                wire.addStatement("builder.registerSingleton($T.class, $N)", beanClass, varName);
            }
        }
    }
    
    private void emitCrossModuleDependency(MethodSpec.Builder wire, BeanDefinition dep) {
        if (target == EmissionTarget.BUILDER) {
            wire.addStatement("$T $N = builder.getBean($T.class)", depClass, depVar, depClass);
        } else {
            // MONOLITHIC: reference local variable (already generated earlier)
            wire.addStatement("$T $N = ...", depClass, depVar);
        }
    }
}
```

### Modified `AppBootstrapGenerator`

Add validation phase:

```java
public void generate(List<String> initializerClassNames, File outputDir) {
    // ... existing launch method generation ...
    
    // After all initializer.register(builder) calls:
    launchMethod.addComment("Validation Phase");
    launchMethod.beginControlFlow("for (Object bean : builder.getSingletons().values())");
    launchMethod.beginControlFlow("if (bean instanceof $T validator)",
        ClassName.get("summer.core.validation", "Validator"));
    launchMethod.addStatement("$T target = builder.getBean(validator.targetType())",
        ClassName.get(Object.class));
    launchMethod.beginControlFlow("if (target != null)");
    launchMethod.addStatement("validator.validate(target)");
    launchMethod.endControlFlow();
    launchMethod.endControlFlow();
    launchMethod.endControlFlow();
    
    launchMethod.addStatement("return builder.build()");
}
```

### Modified `SummerMojo.execute()`

```java
@Override
public void execute() throws MojoExecutionException, MojoFailureException {
    // 1. Load all module indexes
    List<ModuleIndex> modules = loadModules();
    CompositeIndex index = CompositeIndex.create(modules.stream()
        .map(ModuleIndex::index).toList());
    
    // 2-4. Same as before (clean, rowmapper, reindex)
    
    // 5. Discover beans with module provenance
    Map<String, String> classToModule = buildClassToModuleMap(modules);
    List<BeanDefinition> beans = new BeanDiscovery(index).discover(null);
    assignModuleIds(beans, classToModule);
    
    // 6. Resolve dependencies
    List<BeanDefinition> sorted = new DependencyResolver().resolve(beans);
    
    // 7. Generate monolithic AOT context (fallback)
    new AotContextGenerator().generate(sorted, generatedDir);
    new AotProxyGenerator().generate(sorted, generatedDir);
    new RouteAdapterGenerator().generate(sorted, generatedDir);
    
    // 7.5. Generate per-module initializers
    new ModuleInitializerGenerator().generate(sorted, generatedDir);
    
    // 8. Generate AppBootstrap
    List<String> initializerNames = discoverModuleInitializers(index);
    // Also add generated initializers
    initializerNames.addAll(findGeneratedInitializers(sorted));
    new AppBootstrapGenerator().generate(initializerNames, generatedDir);
    
    // 9. Compile
    compileGeneratedSources(generatedDir);
}
```

### `assignModuleIds()`

```java
private void assignModuleIds(List<BeanDefinition> beans, Map<String, String> classToModule) {
    for (BeanDefinition bean : beans) {
        bean.moduleId = classToModule.getOrDefault(bean.qualifiedName, "app");
        // FactoryBeans belong to the same module as their config class
        if (bean instanceof FactoryBean fb && fb.configClassName != null) {
            bean.moduleId = classToModule.getOrDefault(fb.configClassName, bean.moduleId);
        }
    }
}
```

## Cross-Module Dependency Example

Given:
- `summer-fixtures` module: `GreeterService` (implements `Greeter`)
- `summer-fixtures` module: `RecordingInterceptor`
- `app` module: `UserController` (depends on `UserService` from fixtures)

Generated initializers:

```java
// summer.core.aot.SummerFixturesModuleInitializer
public final class SummerFixturesModuleInitializer implements ModuleInitializer {
    @Override
    public void register(ApplicationContext.Builder builder) {
        RecordingInterceptor recordingInterceptor = new RecordingInterceptor();
        builder.registerSingleton(RecordingInterceptor.class, recordingInterceptor);
        
        GreeterService greeterService = new GreeterService();
        builder.registerSingleton(GreeterService.class, greeterService);
        builder.registerSingleton(Greeter.class, greeterService);
    }
}

// summer.core.aot.AppModuleInitializer
public final class AppModuleInitializer implements ModuleInitializer {
    @Override
    public void register(ApplicationContext.Builder builder) {
        // Cross-module: get from builder
        summer.fixtures.aop.Greeter greeter = builder.getBean(summer.fixtures.aop.Greeter.class);
        
        // Module's own beans
        UserController userController = new UserController();
        builder.registerSingleton(UserController.class, userController);
    }
}
```

## Testing Strategy

1. **Unit tests**: Test `ModuleInitializerGenerator` output for known bean sets
2. **Integration tests**: Verify `AppBootstrap.launch()` produces same context as
   `GeneratedAotContext`
3. **TCK**: Both Runtime and AOT engines must pass existing TCK tests
4. **Manual verification**: Compare generated initializer source files

## Migration Path

1. Phase 1: Add `getBean()` to Builder, add `moduleId` to BeanDefinition
2. Phase 2: Track module provenance in SummerMojo
3. Phase 3: Implement ModuleInitializerGenerator
4. Phase 4: Update AppBootstrapGenerator with validation phase
5. Phase 5: Wire everything together in SummerMojo
6. Phase 6: Test and verify
