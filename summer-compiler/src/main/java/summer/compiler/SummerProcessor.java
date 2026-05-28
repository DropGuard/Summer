package summer.compiler;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import summer.aop.Intercepts;
import summer.aop.MethodInterceptor;
import summer.core.Component;
import summer.core.Provider;
import summer.core.annotation.Configuration;
import summer.core.annotation.Bean;


import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import java.io.InputStream;
import java.net.URL;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "summer.core.Component",
        "summer.core.annotation.Configuration",
        "summer.web.annotation.RestController",
        "summer.web.annotation.GlobalMiddleware",
        "summer.data.jdbc.annotation.RowModel"
})
public class SummerProcessor extends AbstractProcessor {

    /** All beans collected across processing rounds. */
    private final List<BeanDefinition> allBeans = new ArrayList<>();
    private boolean aotGenerated = false;
    private boolean generatedNewTypesInThisRound = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[Summer AOT] Processing over. Collected " + allBeans.size() + " beans.");
            return false;
        }

        generatedNewTypesInThisRound = false;

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "[Summer AOT] Round with annotations: " + annotations);

        // --- Collect @Component beans ---
        for (Element e : roundEnv.getElementsAnnotatedWith(Component.class)) {
            if (e.getKind() == ElementKind.CLASS) {
                collectComponent((TypeElement) e);
            }
        }

        // --- Collect @Configuration beans + @Bean factory products ---
        for (Element e : roundEnv.getElementsAnnotatedWith(Configuration.class)) {
            if (e.getKind() == ElementKind.CLASS) {
                TypeElement configClass = (TypeElement) e;
                collectConfiguration(configClass);
                // Keep existing Provider generation for runtime compatibility
                generateProviders(configClass);
            }
        }

        // --- Collect @RestController beans (string-based, may not be on processor classpath) ---
        collectByAnnotationName("summer.web.annotation.RestController", roundEnv);

        // --- Collect @GlobalMiddleware beans ---
        collectByAnnotationName("summer.web.annotation.GlobalMiddleware", roundEnv);

        // --- Generate RowMappers for @RowModel ---
        TypeElement rowModelType = processingEnv.getElementUtils().getTypeElement("summer.data.jdbc.annotation.RowModel");
        if (rowModelType != null) {
            for (Element e : roundEnv.getElementsAnnotatedWith(rowModelType)) {
                if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.CLASS) {
                    generateRowMapper((TypeElement) e);
                }
            }
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                "[Summer AOT] After round: " + allBeans.size() + " beans collected so far.");

        if (!generatedNewTypesInThisRound && !allBeans.isEmpty() && !aotGenerated) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[Summer AOT] Generating AOT context in round where no new types were generated.");
            try {
                generateAotContext();
            } catch (IOException e) {
                error("AOT generation failed: " + e.getMessage(), null);
            }
            aotGenerated = true;
        }

        return true;
    }

    // -----------------------------------------------------------------------
    // Bean collection
    // -----------------------------------------------------------------------

    private void collectComponent(TypeElement typeElement) {
        if (alreadyCollected(typeElement)) return;
        if (isTestInnerClass(typeElement)) return;
        // Skip annotations and interfaces — only concrete classes can be beans
        if (typeElement.getKind() != ElementKind.CLASS) return;

        // Skip generated Provider wrappers — the direct @Bean call already handles them
        if (isGeneratedProvider(typeElement)) return;

        if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
            error("Bean class must be public for AOT compilation: " + typeElement.getQualifiedName(), typeElement);
            return;
        }

        BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.COMPONENT, typeElement);
        fillConstructorInfo(bean);
        fillInterfaces(bean);
        bean.isAutoCloseable = isAutoCloseable(typeElement);
        bean.variableName = toVariableName(typeElement.getSimpleName().toString());
        allBeans.add(bean);
    }

    private void collectConfiguration(TypeElement typeElement) {
        if (alreadyCollected(typeElement)) return;
        if (isTestInnerClass(typeElement)) return;

        if (!typeElement.getModifiers().contains(Modifier.PUBLIC)) {
            error("Configuration class must be public for AOT compilation: " + typeElement.getQualifiedName(), typeElement);
            return;
        }

        // The @Configuration class itself is a bean
        BeanDefinition configBean = new BeanDefinition(BeanDefinition.Kind.CONFIGURATION, typeElement);
        fillConstructorInfo(configBean);
        configBean.isAutoCloseable = isAutoCloseable(typeElement);
        configBean.variableName = toVariableName(typeElement.getSimpleName().toString());
        allBeans.add(configBean);

        // Each @Bean method creates a FACTORY_PRODUCT bean
        for (ExecutableElement method : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (method.getAnnotation(Bean.class) != null) {
                collectFactoryProduct(typeElement, method);
            }
        }
    }

    private void collectFactoryProduct(TypeElement configClass, ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        TypeElement returnElement = asTypeElement(returnType);
        if (returnElement == null) {
            error("@Bean method return type is not a declared type: " + returnType, method);
            return;
        }

        BeanDefinition bean = new BeanDefinition(BeanDefinition.Kind.FACTORY_PRODUCT, returnElement);
        bean.configClass = configClass;
        bean.producerMethod = method;
        bean.producedType = returnType;
        bean.isAutoCloseable = isAutoCloseable(returnElement);
        bean.variableName = toVariableName(returnElement.getSimpleName().toString());

        for (VariableElement param : method.getParameters()) {
            bean.producerParamTypes.add(param.asType());
        }

        allBeans.add(bean);
    }

    private void collectByAnnotationName(String annotationFqn, RoundEnvironment roundEnv) {
        TypeElement annotationType = processingEnv.getElementUtils().getTypeElement(annotationFqn);
        if (annotationType == null) return; // annotation not on classpath

        for (Element e : roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (e.getKind() == ElementKind.CLASS) {
                collectComponent((TypeElement) e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // AOT Context generation
    // -----------------------------------------------------------------------

    private CompositeIndex jandexIndex; // cached in generateAotContext

    private void generateAotContext() throws IOException {
        CompositeIndex index = JandexDiscovery.loadIndex();
        this.jandexIndex = index;

        JandexDiscovery.BeanCollector collector = new JandexDiscovery.BeanCollector() {
            @Override public void collectComponent(TypeElement te) { SummerProcessor.this.collectComponent(te); }
            @Override public void collectConfiguration(TypeElement te) { SummerProcessor.this.collectConfiguration(te); }
            @Override public boolean alreadyCollected(TypeElement te) { return SummerProcessor.this.alreadyCollected(te); }
        };

        // Auto-discover framework beans from SPI modules
        JandexDiscovery.discoverFrameworkBeans(allBeans, index, processingEnv, collector);

        // Auto-discover transitive dependencies from classpath
        JandexDiscovery.discoverTransitiveDependencies(allBeans, index, processingEnv, collector);

        // Discover interceptor beans from classpath that aren't yet collected
        JandexDiscovery.discoverInterceptorBeans(allBeans, index, processingEnv, collector);

        // Discover transitive deps again (interceptors may bring new dependencies)
        JandexDiscovery.discoverTransitiveDependencies(allBeans, index, processingEnv, collector);

        // Resolve @Replaces: remove replaced configs and their @Bean products
        resolveReplacements();

        // Evaluate @ConditionalOnBean: remove components whose conditions are not met
        evaluateConditions();

        // Assign unique variable names
        resolveVariableNameConflicts();

        // Analyze AOP: detect interceptors and mark target beans for proxying
        analyzeAop();

        // Resolve dependencies and topological sort
        Types typeUtils = processingEnv.getTypeUtils();
        DependencyResolver resolver = new DependencyResolver(typeUtils, processingEnv.getMessager());
        List<BeanDefinition> sorted = resolver.resolve(allBeans);

        if (sorted.isEmpty()) {
            error("Dependency resolution failed — cannot generate AOT context.", null);
            return;
        }

        // Generate AOT Proxies for beans that need them
        for (BeanDefinition bean : sorted) {
            if (bean.needsProxy) {
                AotProxyGenerator.generate(bean, processingEnv);
                generatedNewTypesInThisRound = true;
            }
        }

        // Generate AOT Web Route Adapter
        boolean generateWebAdapter = false;
        TypeElement restControllerType = processingEnv.getElementUtils().getTypeElement("summer.web.annotation.RestController");
        if (restControllerType != null) {
            boolean hasWebController = sorted.stream().anyMatch(b -> AnnotationHelper.hasAnnotation(b.typeElement, "summer.web.annotation.RestController"));
            boolean hasExceptionHandler = sorted.stream().anyMatch(b -> ElementFilter.methodsIn(b.typeElement.getEnclosedElements()).stream()
                    .anyMatch(m -> AnnotationHelper.hasAnnotation(m, "summer.web.annotation.ExceptionHandler")));
            generateWebAdapter = hasWebController || hasExceptionHandler;
        }

        if (generateWebAdapter) {
            RouteAdapterGenerator.generate(sorted, processingEnv);
            generatedNewTypesInThisRound = true;
        }

        // Generate the class
        AotContextGenerator generator = new AotContextGenerator(
                processingEnv.getFiler(),
                processingEnv.getMessager()
        );
        generator.generate(sorted, generateWebAdapter);
    }


    // -----------------------------------------------------------------------
    // AOP analysis
    // -----------------------------------------------------------------------

    private void analyzeAop() {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement interceptorType = processingEnv.getElementUtils()
                .getTypeElement("summer.aop.MethodInterceptor");
        if (interceptorType == null) return;

        List<BeanDefinition> interceptorBeans = new ArrayList<>();
        for (BeanDefinition bean : allBeans) {
            if (typeUtils.isAssignable(
                    typeUtils.erasure(bean.typeElement.asType()),
                    typeUtils.erasure(interceptorType.asType()))) {
                interceptorBeans.add(bean);
            }
        }

        if (interceptorBeans.isEmpty()) return;

        for (BeanDefinition bean : allBeans) {
            if (interceptorBeans.contains(bean)) continue;
            if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT) continue;
            if (bean.interfaces.isEmpty()) continue;

            boolean hasIntercepted = AotProxyGenerator.beanHasMethodsWithAnnotation(bean.typeElement, "summer.aop.Intercepted");

            boolean matchesAnyStaticTrigger = false;
            List<BeanDefinition> matchingStaticInterceptors = new ArrayList<>();
            List<BeanDefinition> dynamicInterceptors = new ArrayList<>();

            for (BeanDefinition interceptor : interceptorBeans) {
                List<TypeMirror> targets = AotProxyGenerator.getInterceptsAnnotations(interceptor.typeElement);
                if (targets.isEmpty()) {
                    dynamicInterceptors.add(interceptor);
                } else {
                    if (AotProxyGenerator.beanHasAnnotatedMethods(bean.typeElement, targets, processingEnv)) {
                        matchesAnyStaticTrigger = true;
                        matchingStaticInterceptors.add(interceptor);
                    } else if (hasIntercepted) {
                        matchingStaticInterceptors.add(interceptor);
                    }
                }
            }

            boolean needsProxy = hasIntercepted || matchesAnyStaticTrigger;
            if (needsProxy) {
                bean.needsProxy = true;
                bean.interceptors.addAll(matchingStaticInterceptors);
                bean.interceptors.addAll(dynamicInterceptors);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: fill bean metadata
    // -----------------------------------------------------------------------

    private void fillConstructorInfo(BeanDefinition bean) {
        List<ExecutableElement> constructors = ElementFilter
                .constructorsIn(bean.typeElement.getEnclosedElements())
                .stream()
                .filter(c -> c.getModifiers().contains(Modifier.PUBLIC))
                .toList();

        if (constructors.isEmpty()) {
            error("No public constructor found for " + bean.qualifiedName(), bean.typeElement);
            return;
        }
        if (constructors.size() != 1) {
            error("Component " + bean.qualifiedName()
                    + " must have exactly ONE public constructor. Found: " + constructors.size(), bean.typeElement);
            return;
        }
        ExecutableElement ctor = constructors.getFirst();

        bean.constructor = ctor;
        for (VariableElement param : ctor.getParameters()) {
            bean.constructorParamTypes.add(param.asType());
        }
    }

    private void fillInterfaces(BeanDefinition bean) {
        for (TypeMirror iface : bean.typeElement.getInterfaces()) {
            bean.interfaces.add(iface);
        }
    }

    // -----------------------------------------------------------------------
    // Helper: naming
    // -----------------------------------------------------------------------

    private String toVariableName(String simpleName) {
        if (simpleName.isEmpty()) return "bean";
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private void resolveVariableNameConflicts() {
        Set<String> usedNames = new HashSet<>();
        for (BeanDefinition bean : allBeans) {
            String base = bean.variableName;
            String name = base;
            int suffix = 2;
            while (!usedNames.add(name)) {
                name = base + suffix++;
            }
            bean.variableName = name;
        }
    }

    // -----------------------------------------------------------------------
    // Helper: type checks
    // -----------------------------------------------------------------------

    private boolean alreadyCollected(TypeElement typeElement) {
        String qn = typeElement.getQualifiedName().toString();
        return allBeans.stream().anyMatch(b -> b.qualifiedName().equals(qn));
    }

    private boolean isAutoCloseable(TypeElement typeElement) {
        TypeElement acElement = processingEnv.getElementUtils()
                .getTypeElement("java.lang.AutoCloseable");
        if (acElement == null) return false;
        return processingEnv.getTypeUtils().isAssignable(
                processingEnv.getTypeUtils().erasure(typeElement.asType()),
                processingEnv.getTypeUtils().erasure(acElement.asType()));
    }

    /** Checks if a type is a generated Provider wrapper (e.g., Foo_Bar_Provider). */
    private boolean isGeneratedProvider(TypeElement typeElement) {
        String name = typeElement.getSimpleName().toString();
        if (!name.endsWith("_Provider")) return false;

        TypeElement providerType = processingEnv.getElementUtils()
                .getTypeElement("summer.core.Provider");
        if (providerType == null) return false;

        return processingEnv.getTypeUtils().isAssignable(
                processingEnv.getTypeUtils().erasure(typeElement.asType()),
                processingEnv.getTypeUtils().erasure(providerType.asType()));
    }


    /**
     * Checks if a class is an inner class whose enclosing class is not a
     * framework bean. This filters out test @Configuration inner classes
     * (e.g. RuntimeBeanTest$TestConfig) that shouldn't be in the AOT context.
     */
    private boolean isTestInnerClass(TypeElement typeElement) {
        Element enclosing = typeElement.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() != ElementKind.CLASS) return false;
        TypeElement enclosingClass = (TypeElement) enclosing;
        // Only include inner classes if the enclosing class is itself a bean
        return !AnnotationHelper.hasAnnotation(enclosingClass, "summer.core.Component")
                && !AnnotationHelper.hasAnnotation(enclosingClass, "summer.core.annotation.Configuration")
                && !AnnotationHelper.hasAnnotation(enclosingClass, "summer.web.annotation.RestController");
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
        javax.lang.model.element.Element element = processingEnv.getTypeUtils().asElement(typeMirror);
        return (element instanceof TypeElement te) ? te : null;
    }

    private void error(String msg, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
    }

    // -----------------------------------------------------------------------
    // RowMapper Generation for @RowModel
    // -----------------------------------------------------------------------

    private void generateRowMapper(TypeElement rowModelElement) {
        String packageName = processingEnv.getElementUtils().getPackageOf(rowModelElement).getQualifiedName().toString();
        String className = rowModelElement.getSimpleName().toString() + "_RowMapper";

        ClassName rowModelClass = ClassName.get(rowModelElement);
        ClassName rowMapperInterface = ClassName.get("summer.data.jdbc", "RowMapper");
        TypeName genericRowMapper = ParameterizedTypeName.get(rowMapperInterface, rowModelClass);

        TypeSpec.Builder mapperBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(genericRowMapper);

        MethodSpec.Builder mapRowMethod = MethodSpec.methodBuilder("mapRow")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(rowModelClass)
                .addParameter(ClassName.get("java.sql", "ResultSet"), "rs")
                .addParameter(int.class, "rowNum")
                .addException(ClassName.get("java.sql", "SQLException"));

        if (rowModelElement.getKind() == ElementKind.RECORD) {
            List<? extends RecordComponentElement> recordComponents = rowModelElement.getRecordComponents();
            StringBuilder args = new StringBuilder();
            
            for (RecordComponentElement comp : recordComponents) {
                if (args.length() > 0) args.append(", ");
                String name = comp.getSimpleName().toString();
                String type = comp.asType().toString();
                
                if (type.equals("int") || type.equals("java.lang.Integer")) {
                    args.append("rs.getInt(\"").append(name).append("\")");
                } else if (type.equals("long") || type.equals("java.lang.Long")) {
                    args.append("rs.getLong(\"").append(name).append("\")");
                } else if (type.equals("double") || type.equals("java.lang.Double")) {
                    args.append("rs.getDouble(\"").append(name).append("\")");
                } else if (type.equals("boolean") || type.equals("java.lang.Boolean")) {
                    args.append("rs.getBoolean(\"").append(name).append("\")");
                } else if (type.equals("java.lang.String")) {
                    args.append("rs.getString(\"").append(name).append("\")");
                } else {
                    args.append("(").append(type).append(") rs.getObject(\"").append(name).append("\")");
                }
            }
            mapRowMethod.addStatement("return new $T(" + args.toString() + ")", rowModelClass);
        } else {
            // For POJOs, we would find a no-arg constructor and generate setters.
            // For now, we strictly support Records as per plan.
            error("@RowModel is currently only supported on Java Records", rowModelElement);
            return;
        }

        mapperBuilder.addMethod(mapRowMethod.build());

        JavaFile javaFile = JavaFile.builder(packageName, mapperBuilder.build()).build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
            generatedNewTypesInThisRound = true;
        } catch (IOException e) {
            // Ignore if already written
        }
    }

    // -----------------------------------------------------------------------
    // Existing Provider generation (kept for runtime scanner compatibility)
    // -----------------------------------------------------------------------

    private void generateProviders(TypeElement configClass) {
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(configClass).getQualifiedName().toString() + ".generated";

        for (Element enclosed : configClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD && enclosed.getAnnotation(Bean.class) != null) {
                ExecutableElement method = (ExecutableElement) enclosed;
                generateProvider(packageName, configClass, method);
            }
        }
    }

    private void generateProvider(String packageName, TypeElement configClass, ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        String methodName = method.getSimpleName().toString();
        String providerClassName = configClass.getSimpleName() + "_" + capitalize(methodName) + "_Provider";

        TypeSpec.Builder providerSpecBuilder = TypeSpec.classBuilder(providerClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Component.class)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Provider.class), TypeName.get(returnType)))
                .addField(TypeName.get(configClass.asType()), "config", Modifier.PRIVATE, Modifier.FINAL);

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.get(configClass.asType()), "config")
                .addStatement("this.config = config");

        StringBuilder methodCallArgs = new StringBuilder();

        for (VariableElement param : method.getParameters()) {
            String paramName = param.getSimpleName().toString();
            TypeName paramType = TypeName.get(param.asType());

            providerSpecBuilder.addField(paramType, paramName, Modifier.PRIVATE, Modifier.FINAL);
            constructorBuilder.addParameter(paramType, paramName);
            constructorBuilder.addStatement("this.$N = $N", paramName, paramName);

            if (methodCallArgs.length() > 0) {
                methodCallArgs.append(", ");
            }
            methodCallArgs.append("this.").append(paramName);
        }

        providerSpecBuilder.addMethod(constructorBuilder.build());

        providerSpecBuilder.addMethod(MethodSpec.methodBuilder("provide")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.get(returnType))
                .addStatement("return config.$L(" + methodCallArgs.toString() + ")", methodName)
                .build());

        JavaFile javaFile = JavaFile.builder(packageName, providerSpecBuilder.build()).build();

        try {
            javaFile.writeTo(processingEnv.getFiler());
            generatedNewTypesInThisRound = true;
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate provider: " + e.getMessage());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Removes components whose {@code @ConditionalOnBean} conditions are not met.
     * Iterates until stable, because removing one component may un-satisfy another's condition.
     */
    private void evaluateConditions() {
        Types typeUtils = processingEnv.getTypeUtils();

        boolean changed = true;
        while (changed) {
            changed = false;
            List<BeanDefinition> toRemove = new ArrayList<>();
            for (BeanDefinition bean : allBeans) {
                AnnotationMirror condMirror = AnnotationHelper.getAnnotationMirror(bean.typeElement,
                        "summer.core.annotation.ConditionalOnBean");
                if (condMirror == null) continue;

                // Get the value() from the annotation
                Object value = AnnotationHelper.getAnnotationClassValue(condMirror, processingEnv);
                if (!(value instanceof TypeMirror requiredType)) continue;

                // Check if any collected bean satisfies the required type
                boolean satisfied = false;
                for (BeanDefinition other : allBeans) {
                    if (other == bean) continue;
                    if (typeUtils.isAssignable(
                            typeUtils.erasure(other.typeElement.asType()),
                            typeUtils.erasure(requiredType))) {
                        satisfied = true;
                        break;
                    }
                }
                if (!satisfied) {
                    toRemove.add(bean);
                }
            }
            if (!toRemove.isEmpty()) {
                allBeans.removeAll(toRemove);
                changed = true;
            }
        }
    }

    /**
     * Resolves @Replaces annotations: removes replaced @Configuration classes
     * and their @Bean factory products from allBeans.
     */
    private void resolveReplacements() {
        Map<TypeElement, TypeElement> replacementMap = new HashMap<>(); // replaced -> replacement

        for (BeanDefinition bean : allBeans) {
            if (bean.kind != BeanDefinition.Kind.CONFIGURATION) continue;
            if (!AnnotationHelper.hasAnnotation(bean.typeElement, "summer.core.annotation.Replaces")) continue;

            List<TypeMirror> targets = AnnotationHelper.getAnnotationClassListValue(bean.typeElement, "summer.core.annotation.Replaces", processingEnv);
            if (targets.isEmpty()) continue;

            TypeElement targetElement = asTypeElement(targets.get(0));
            if (targetElement == null) continue;

            if (replacementMap.containsKey(targetElement)) {
                error("Duplicate @Replaces: both " + replacementMap.get(targetElement).getQualifiedName()
                        + " and " + bean.typeElement.getQualifiedName()
                        + " replace " + targetElement.getQualifiedName(), bean.typeElement);
                return;
            }

            replacementMap.put(targetElement, bean.typeElement);
        }

        if (replacementMap.isEmpty()) return;

        // Remove replaced CONFIGURATION beans and their FACTORY_PRODUCT beans
        Set<String> replacedNames = new HashSet<>();
        for (TypeElement replaced : replacementMap.keySet()) {
            replacedNames.add(replaced.getQualifiedName().toString());
        }

        allBeans.removeIf(bean ->
                replacedNames.contains(bean.qualifiedName())
                || (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT
                    && bean.configClass != null
                    && replacedNames.contains(bean.configClass.getQualifiedName().toString()))
        );
    }

}
