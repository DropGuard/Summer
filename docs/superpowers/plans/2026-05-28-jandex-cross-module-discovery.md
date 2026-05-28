# Jandex Cross-Module Bean Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `META-INF/summer-beans.txt` and hardcoded framework knowledge in `SummerProcessor` with Jandex `.idx`-based generic discovery, so any new framework module is auto-discovered without compiler changes.

**Architecture:** Every module (framework + user) generates `META-INF/jandex.idx` via `jandex-maven-plugin` (already configured for framework modules). `SummerProcessor` loads these indexes at compile time via `ClassLoader.getResources("META-INF/jandex.idx")` and uses them for component discovery, interceptor discovery, and interface→implementation resolution. The runtime `ComponentScanner` already uses Jandex — only Javadoc cleanup needed there.

**Tech Stack:** Jandex 3.5.3 (SmallRye), `jandex-maven-plugin` 3.5.3, Java 25 APT (`javax.annotation.processing`)

---

### Task 1: Add Jandex Dependency to summer-compiler

The `SummerProcessor` needs the Jandex library to read `.idx` files at compile time. The Jandex version is already managed in the parent POM's `<dependencyManagement>`.

**Files:**
- Modify: `summer-compiler/pom.xml:17-71` (dependencies section)

- [ ] **Step 1: Add jandex dependency to summer-compiler/pom.xml**

Add after the `summer-aop` dependency block (line 31):

```xml
        <!-- Jandex for reading pre-built class indexes at compile time -->
        <dependency>
            <groupId>io.smallrye</groupId>
            <artifactId>jandex</artifactId>
        </dependency>
```

- [ ] **Step 2: Verify the dependency resolves**

Run: `mvn -pl summer-compiler dependency:resolve -q`
Expected: BUILD SUCCESS (no errors about missing jandex artifact)

- [ ] **Step 3: Commit**

```bash
git add summer-compiler/pom.xml
git commit -m "build: add jandex dependency to summer-compiler for index reading"
```

---

### Task 2: Add jandex-maven-plugin to summer-example

All framework modules already have `jandex-maven-plugin` configured. The user application module (`summer-example`) needs it too so its classes are indexed and available on the classpath for both the runtime `CompositeIndex` and future downstream consumers.

**Files:**
- Modify: `summer-example/pom.xml:137-172` (build/plugins section)

- [ ] **Step 1: Add jandex-maven-plugin to summer-example/pom.xml**

Add inside `<plugins>`, after the existing `maven-compiler-plugin` block (after line 171, before `</plugins>`):

```xml
            <plugin>
                <groupId>io.smallrye</groupId>
                <artifactId>jandex-maven-plugin</artifactId>
            </plugin>
```

The plugin's `<executions>` config is inherited from the parent POM's `<pluginManagement>`.

- [ ] **Step 2: Verify the plugin runs**

Run: `mvn -pl summer-example process-classes -q`
Expected: BUILD SUCCESS, and `summer-example/target/classes/META-INF/jandex.idx` exists.

- [ ] **Step 3: Commit**

```bash
git add summer-example/pom.xml
git commit -m "build: add jandex-maven-plugin to summer-example for user code indexing"
```

---

### Task 3: Implement Jandex Index Loading in SummerProcessor

Add a method that loads all `META-INF/jandex.idx` files from the compiler classpath and returns a `CompositeIndex`. This is the foundation that Tasks 4, 5, and 6 build on.

**Files:**
- Modify: `summer-compiler/src/main/java/summer/compiler/SummerProcessor.java`

- [ ] **Step 1: Add Jandex imports**

At the top of `SummerProcessor.java`, add after the existing imports (after line 21, before the `@AutoService` annotation):

```java
import org.jboss.jandex.*;
import java.io.InputStream;
import java.net.URL;
```

- [ ] **Step 2: Add a field to cache the loaded index**

Add inside the `SummerProcessor` class, after the `generatedNewTypesInThisRound` field (line 37):

```java
    private CompositeIndex jandexIndex;
```

- [ ] **Step 3: Add the index loading method**

Add after the `discoverFrameworkBeans()` method (after line 1147):

```java
    /**
     * Loads all pre-built Jandex indexes from dependency JARs on the compiler classpath.
     * Returns a CompositeIndex merging all discovered META-INF/jandex.idx files.
     * Caches the result so it's loaded only once per processor lifecycle.
     */
    private CompositeIndex loadJandexIndex() {
        if (jandexIndex != null) return jandexIndex;

        List<IndexView> indexes = new ArrayList<>();
        try {
            ClassLoader cl = this.getClass().getClassLoader();
            Enumeration<URL> urls = cl.getResources("META-INF/jandex.idx");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream is = url.openStream()) {
                    indexes.add(new IndexReader(is).read());
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                            "[Summer AOT] Loaded Jandex index from " + url);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                            "[Summer AOT] Failed to read Jandex index from " + url + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "[Summer AOT] Failed to enumerate Jandex indexes: " + e.getMessage());
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "[Summer AOT] Loaded " + indexes.size() + " Jandex indexes from classpath");
        jandexIndex = CompositeIndex.create(indexes);
        return jandexIndex;
    }
```

- [ ] **Step 4: Verify compilation**

Run: `mvn -pl summer-compiler compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add summer-compiler/src/main/java/summer/compiler/SummerProcessor.java
git commit -m "feat: add Jandex index loading to SummerProcessor"
```

---

### Task 4: Replace discoverFrameworkBeans() with Jandex-Based Discovery

Replace the `summer-beans.txt`-reading method with generic Jandex-based component discovery. The index carries full annotation metadata, so we can find `@Component` (and meta-annotated) classes from any dependency module.

**Files:**
- Modify: `summer-compiler/src/main/java/summer/compiler/SummerProcessor.java:1123-1147` (discoverFrameworkBeans method)

- [ ] **Step 1: Replace the discoverFrameworkBeans() method body**

Replace the entire `discoverFrameworkBeans()` method (lines 1123-1147) with:

```java
    private void discoverFrameworkBeans() {
        CompositeIndex index = loadJandexIndex();

        DotName componentDot = DotName.createSimple("summer.core.annotation.Component");
        DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");

        // Discover @Component-annotated classes from dependency indexes
        for (AnnotationInstance ai : index.getAnnotations(componentDot)) {
            if (ai.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo ci = ai.target().asClass();
            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te != null && !alreadyCollected(te)) {
                collectComponent(te);
            }
        }

        // Discover @Configuration classes (meta-annotated with @Component, but need
        // collectConfiguration to also pick up their @Bean methods)
        for (AnnotationInstance ai : index.getAnnotations(configDot)) {
            if (ai.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo ci = ai.target().asClass();
            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te != null && !alreadyCollected(te)) {
                collectConfiguration(te);
            }
        }

        // Discover meta-annotated components: @RestController, @GlobalMiddleware, etc.
        // These annotations are themselves annotated with @Component.
        // Find annotation types that carry @Component, then find classes annotated with those.
        for (AnnotationInstance metaAnn : index.getAnnotations(componentDot)) {
            if (metaAnn.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo annotatedClass = metaAnn.target().asClass();
            // If this class is itself an annotation, it's a meta-annotation like @RestController
            if (!java.lang.annotation.Annotation.class.getName().equals(
                    annotatedClass.superName() != null ? annotatedClass.superName().toString() : "")) {
                // Not an annotation — already handled above as a direct @Component
                continue;
            }
            // Find all classes annotated with this meta-annotation
            DotName metaAnnotationName = annotatedClass.name();
            for (AnnotationInstance usage : index.getAnnotations(metaAnnotationName)) {
                if (usage.target().kind() != AnnotationTarget.Kind.CLASS) continue;
                ClassInfo userClass = usage.target().asClass();
                TypeElement te = processingEnv.getElementUtils().getTypeElement(userClass.name().toString());
                if (te != null && !alreadyCollected(te)) {
                    collectComponent(te);
                }
            }
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "[Summer AOT] Jandex-based framework discovery complete. Total beans: " + allBeans.size());
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl summer-compiler compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add summer-compiler/src/main/java/summer/compiler/SummerProcessor.java
git commit -m "feat: replace summer-beans.txt reading with Jandex-based component discovery"
```

---

### Task 5: Replace discoverInterceptorBeans() Hardcoded Map with Jandex

Replace the hardcoded `knownInterceptors` map with generic `getAllKnownImplementors(MethodInterceptor)` from the Jandex index. Any module shipping a `MethodInterceptor` with `@Intercepts` is now auto-discovered.

**Files:**
- Modify: `summer-compiler/src/main/java/summer/compiler/SummerProcessor.java:381-430` (discoverInterceptorBeans method)

- [ ] **Step 1: Replace the discoverInterceptorBeans() method body**

Replace the entire `discoverInterceptorBeans()` method (lines 381-430) with:

```java
    private void discoverInterceptorBeans() {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement miType = processingEnv.getElementUtils()
                .getTypeElement("summer.aop.MethodInterceptor");
        if (miType == null) return;

        // Check if we already have interceptor beans (e.g., from direct roundEnv collection)
        boolean hasInterceptors = allBeans.stream().anyMatch(b ->
                typeUtils.isAssignable(
                        typeUtils.erasure(b.typeElement.asType()),
                        typeUtils.erasure(miType.asType())));
        if (hasInterceptors) return;

        // Use Jandex to find all MethodInterceptor implementors from dependency indexes
        CompositeIndex index = loadJandexIndex();
        DotName miDot = DotName.createSimple("summer.aop.MethodInterceptor");
        DotName interceptsDot = DotName.createSimple("summer.aop.Intercepts");

        for (ClassInfo ci : index.getAllKnownImplementors(miDot)) {
            AnnotationInstance intercepts = ci.annotation(interceptsDot);
            if (intercepts == null) continue;

            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te == null) continue;
            if (alreadyCollected(te)) continue;

            // Extract the target annotation from @Intercepts to check if any bean uses it
            String targetAnnotationFqn = null;
            AnnotationValue annValue = intercepts.value("annotations");
            if (annValue != null) {
                var annotationTypes = annValue.asClassArray();
                if (annotationTypes.length > 0) {
                    targetAnnotationFqn = annotationTypes[0].name().toString();
                }
            }

            if (targetAnnotationFqn != null) {
                // Only collect if at least one bean has a method with the target annotation
                String finalTargetFqn = targetAnnotationFqn;
                boolean hasTarget = allBeans.stream().anyMatch(b ->
                        javax.lang.model.util.ElementFilter.methodsIn(b.typeElement.getEnclosedElements()).stream()
                                .anyMatch(m -> hasAnnotation(m, finalTargetFqn)));

                if (hasTarget && hasAnnotation(te, "summer.core.Component")) {
                    collectComponent(te);
                }
            }
        }
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl summer-compiler compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add summer-compiler/src/main/java/summer/compiler/SummerProcessor.java
git commit -m "feat: replace hardcoded interceptor map with Jandex-based discovery"
```

---

### Task 6: Replace tryDiscoverImplementation() Hardcoded Map with Jandex

The `tryDiscoverImplementation()` method has a second hardcoded mapping (`TransactionManager→SimpleJdbcTransactionManager`, etc.). Replace it with Jandex `getAllKnownImplementors()` so any interface→implementation pair is auto-resolved.

**Files:**
- Modify: `summer-compiler/src/main/java/summer/compiler/SummerProcessor.java:356-374` (tryDiscoverImplementation method)

- [ ] **Step 1: Replace the tryDiscoverImplementation() method body**

Replace the entire `tryDiscoverImplementation()` method (lines 356-374) with:

```java
    private boolean tryDiscoverImplementation(TypeElement interfaceElement) {
        CompositeIndex index = loadJandexIndex();
        DotName ifaceDot = DotName.createSimple(interfaceElement.getQualifiedName().toString());

        for (ClassInfo ci : index.getAllKnownImplementors(ifaceDot)) {
            if (ci.isAbstract() || ci.isInterface()) continue;

            TypeElement implElement = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (implElement != null && tryCollectFromClasspath(implElement)) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl summer-compiler compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add summer-compiler/src/main/java/summer/compiler/SummerProcessor.java
git commit -m "feat: replace hardcoded interface-impl map with Jandex implementor lookup"
```

---

### Task 7: Remove summer-beans.txt Generation

The `generateBeanRegistry()` method and its call site in `process()` are no longer needed. Remove them.

**Files:**
- Modify: `summer-compiler/src/main/java/summer/compiler/SummerProcessor.java`

- [ ] **Step 1: Remove the call to generateBeanRegistry() in the process() method**

In the `process()` method (around lines 46-53), remove the `generateBeanRegistry` call block. Replace:

```java
        if (roundEnv.processingOver()) {
            if (!allBeans.isEmpty()) {
                generateBeanRegistry(allBeans);
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[Summer AOT] Processing over. Collected " + allBeans.size() + " beans.");
            return false;
        }
```

With:

```java
        if (roundEnv.processingOver()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[Summer AOT] Processing over. Collected " + allBeans.size() + " beans.");
            return false;
        }
```

- [ ] **Step 2: Delete the generateBeanRegistry() method**

Remove the entire `generateBeanRegistry()` method (lines 257-277):

```java
    /**
     * Generates META-INF/summer-beans.txt for APT cross-module bean discovery.
     * Other modules' APT processors read this file to discover beans from this module.
     */
    private void generateBeanRegistry(List<BeanDefinition> beans) {
        ...
    }
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl summer-compiler compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add summer-compiler/src/main/java/summer/compiler/SummerProcessor.java
git commit -m "refactor: remove summer-beans.txt generation (replaced by jandex.idx)"
```

---

### Task 8: Update Runtime Javadocs

The runtime `ComponentScanner` and `RuntimeApplicationContext` have Javadoc comments referencing `summer-beans.txt`. These references are outdated — the runtime already uses Jandex indexes only. Update the comments.

**Files:**
- Modify: `summer-runtime/src/main/java/summer/scanner/runtime/ComponentScanner.java:25-29`
- Modify: `summer-runtime/src/main/java/summer/scanner/runtime/RuntimeApplicationContext.java:50-54`

- [ ] **Step 1: Update ComponentScanner Javadoc**

In `ComponentScanner.java`, replace lines 25-29:

```java
/**
 * Component scanner that discovers Summer components using Jandex indexes.
 * Framework modules ship pre-built {@code META-INF/jandex.idx} files;
 * user beans are listed in {@code META-INF/summer-beans.txt} (generated by the APT processor).
 */
```

With:

```java
/**
 * Component scanner that discovers Summer components using Jandex indexes.
 * All modules (framework and user) ship pre-built {@code META-INF/jandex.idx} files
 * generated by the {@code jandex-maven-plugin}. User packages without an index
 * are scanned on-the-fly as a development fallback.
 */
```

- [ ] **Step 2: Update RuntimeApplicationContext Javadoc**

In `RuntimeApplicationContext.java`, replace lines 50-54:

```java
    /**
     * Scans for @Component annotated classes and initializes the context.
     * Framework beans come from pre-built Jandex indexes;
     * user beans come from META-INF/summer-beans.txt and, as fallback, from package scanning.
     */
```

With:

```java
    /**
     * Scans for @Component annotated classes and initializes the context.
     * All modules contribute beans via pre-built {@code META-INF/jandex.idx} files;
     * user packages without an index are scanned on-the-fly as a development fallback.
     */
```

- [ ] **Step 3: Commit**

```bash
git add summer-runtime/src/main/java/summer/scanner/runtime/ComponentScanner.java summer-runtime/src/main/java/summer/scanner/runtime/RuntimeApplicationContext.java
git commit -m "docs: update runtime Javadocs to reflect Jandex-only discovery"
```

---

### Task 9: Full Build Verification

Run the full project build to verify everything compiles, tests pass, and the example app generates its AOT context correctly with Jandex-based discovery.

**Files:** None (verification only)

- [ ] **Step 1: Full project build**

Run: `mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS for all modules

- [ ] **Step 2: Verify summer-example generates AOT context with Jandex**

Run: `mvn -pl summer-example compile -X 2>&1 | grep -i "Summer AOT.*Jandex"`
Expected: Log lines like `[Summer AOT] Loaded N Jandex indexes from classpath` and `[Summer AOT] Jandex-based framework discovery complete`

- [ ] **Step 3: Verify no summer-beans.txt is generated**

Run: `find . -name "summer-beans.txt" -path "*/target/*"`
Expected: No results

- [ ] **Step 4: Verify jandex.idx is generated in summer-example**

Run: `ls summer-example/target/classes/META-INF/jandex.idx`
Expected: File exists

- [ ] **Step 5: Run all tests**

Run: `mvn test -q`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 6: Commit any remaining fixes**

If any tests fail due to the changes, fix them and commit. Otherwise, no action needed.
