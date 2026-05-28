package summer.compiler;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import summer.aop.Intercepts;
import summer.aop.MethodInterceptor;
import summer.core.Component;
import summer.core.Provider;
import summer.core.annotation.Configuration;
import summer.core.annotation.Bean;


import org.jboss.jandex.*;
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

    private CompositeIndex jandexIndex;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            if (!allBeans.isEmpty()) {
                generateBeanRegistry(allBeans);
            }
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
            generateAotContext();
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

    private void generateAotContext() {
        // Auto-discover framework beans from SPI modules
        discoverFrameworkBeans();

        // Auto-discover transitive dependencies from classpath
        discoverTransitiveDependencies();

        // Discover interceptor beans from classpath that aren't yet collected
        discoverInterceptorBeans();

        // Discover transitive deps again (interceptors may bring new dependencies)
        discoverTransitiveDependencies();

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
                generateAotProxy(bean);
            }
        }

        // Generate AOT Web Route Adapter
        boolean generateWebAdapter = false;
        TypeElement restControllerType = processingEnv.getElementUtils().getTypeElement("summer.web.annotation.RestController");
        if (restControllerType != null) {
            boolean hasWebController = sorted.stream().anyMatch(b -> hasAnnotation(b.typeElement, "summer.web.annotation.RestController"));
            boolean hasExceptionHandler = sorted.stream().anyMatch(b -> ElementFilter.methodsIn(b.typeElement.getEnclosedElements()).stream()
                    .anyMatch(m -> hasAnnotation(m, "summer.web.annotation.ExceptionHandler")));
            generateWebAdapter = hasWebController || hasExceptionHandler;
        }

        if (generateWebAdapter) {
            generateRouteAdapter(sorted);
        }

        // Generate the class
        AotContextGenerator generator = new AotContextGenerator(
                processingEnv.getFiler(),
                processingEnv.getMessager()
        );
        generator.generate(sorted, generateWebAdapter);
    }

    /**
     * Generates META-INF/summer-beans.txt for APT cross-module bean discovery.
     * Other modules' APT processors read this file to discover beans from this module.
     */
    private void generateBeanRegistry(List<BeanDefinition> beans) {
        try {
            javax.tools.FileObject registryFile = processingEnv.getFiler().createResource(
                    javax.tools.StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/summer-beans.txt");
            try (java.io.Writer w = registryFile.openWriter()) {
                for (BeanDefinition bean : beans) {
                    w.write(bean.qualifiedName() + "\n");
                }
            }
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE,
                    "[Summer AOT] Generated META-INF/summer-beans.txt with " + beans.size() + " beans");
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR,
                    "Failed to generate summer-beans.txt: " + e.getMessage());
        }
    }

    /**
     * For each bean's constructor/producer params, if the param type isn't among
     * collected beans, try to find it on the classpath and auto-register it.
     */
    private void discoverTransitiveDependencies() {
        boolean changed = true;
        while (changed) {
            changed = false;
            List<TypeMirror> allParamTypes = new ArrayList<>();
            for (BeanDefinition bean : allBeans) {
                if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT) {
                    allParamTypes.addAll(bean.producerParamTypes);
                } else {
                    allParamTypes.addAll(bean.constructorParamTypes);
                }
            }

            for (TypeMirror paramType : allParamTypes) {
                TypeElement paramElement = asTypeElement(paramType);
                if (paramElement == null) continue;

                // Skip if already satisfied (exact match or interface match)
                if (isBeanSatisfied(paramElement)) continue;

                // Direct: check if the type itself is a @Component on the classpath
                if (tryCollectFromClasspath(paramElement)) {
                    changed = true;
                    continue;
                }

                // Interface: if paramType is an interface, look for known implementations
                if (paramElement.getKind() == ElementKind.INTERFACE) {
                    if (tryDiscoverImplementation(paramElement)) {
                        changed = true;
                    }
                }
            }
        }
    }

    /** Checks if a type is already satisfied by an existing bean (exact or interface match). */
    private boolean isBeanSatisfied(TypeElement typeElement) {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeMirror targetType = typeUtils.erasure(typeElement.asType());
        for (BeanDefinition b : allBeans) {
            TypeMirror beanType = typeUtils.erasure(b.typeElement.asType());
            if (typeUtils.isSameType(beanType, targetType)
                    || typeUtils.isAssignable(beanType, targetType)) {
                return true;
            }
        }
        return false;
    }

    /** Tries to collect a type from the classpath if it has a bean annotation. */
    private boolean tryCollectFromClasspath(TypeElement element) {
        if (alreadyCollected(element)) return false;

        if (hasAnnotation(element, "summer.core.Component")
                || hasAnnotation(element, "summer.core.annotation.Configuration")
                || hasAnnotation(element, "summer.web.annotation.RestController")
                || hasAnnotation(element, "summer.web.annotation.GlobalMiddleware")) {

            if (element.getAnnotation(Configuration.class) != null) {
                collectConfiguration(element);
            } else {
                collectComponent(element);
            }
            return true;
        }
        return false;
    }

    /**
     * For an interface type, tries to discover a concrete @Component implementation
     * from known framework packages on the classpath.
     */
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

    /**
     * Discovers MethodInterceptor beans with @Intercepts that are on the classpath
     * but not yet in allBeans. These are needed for AOP proxy wrapping but aren't
     * direct constructor dependencies of any user bean.
     */
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

    // -----------------------------------------------------------------------
    // AOP analysis
    // -----------------------------------------------------------------------

    private void analyzeAop() {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement interceptorType = processingEnv.getElementUtils()
                .getTypeElement("summer.aop.MethodInterceptor");
        if (interceptorType == null) return;

        // Find all MethodInterceptor beans
        List<BeanDefinition> interceptorBeans = new ArrayList<>();
        for (BeanDefinition bean : allBeans) {
            if (typeUtils.isAssignable(
                    typeUtils.erasure(bean.typeElement.asType()),
                    typeUtils.erasure(interceptorType.asType()))) {
                interceptorBeans.add(bean);
            }
        }

        if (interceptorBeans.isEmpty()) return;

        // For each non-interceptor bean, check if it needs to be proxied
        for (BeanDefinition bean : allBeans) {
            if (interceptorBeans.contains(bean)) continue;
            if (bean.kind == BeanDefinition.Kind.FACTORY_PRODUCT) continue;
            if (bean.interfaces.isEmpty()) continue; // JDK proxy requires interfaces

            boolean hasIntercepted = beanHasMethodsWithAnnotation(bean.typeElement, "summer.aop.Intercepted");

            // Check if any static interceptor matches this bean's methods
            boolean matchesAnyStaticTrigger = false;
            List<BeanDefinition> matchingStaticInterceptors = new ArrayList<>();
            List<BeanDefinition> dynamicInterceptors = new ArrayList<>();

            for (BeanDefinition interceptor : interceptorBeans) {
                List<TypeMirror> targets = getInterceptsAnnotations(interceptor.typeElement);
                if (targets.isEmpty()) {
                    // It's a dynamic interceptor
                    dynamicInterceptors.add(interceptor);
                } else {
                    // It's a static interceptor, check if bean has methods annotated with any of its trigger annotations
                    if (beanHasAnnotatedMethods(bean.typeElement, targets)) {
                        matchesAnyStaticTrigger = true;
                        matchingStaticInterceptors.add(interceptor);
                    } else if (hasIntercepted) {
                        // If the bean has @Intercepted, we also include the static interceptor
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

    private boolean beanHasMethodsWithAnnotation(TypeElement bean, String annotationFqn) {
        for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
            if (hasAnnotation(method, annotationFqn)) {
                return true;
            }
        }
        return false;
    }

    private List<TypeMirror> getInterceptsAnnotations(TypeElement element) {
        Intercepts intercepts = element.getAnnotation(Intercepts.class);
        if (intercepts == null) return Collections.emptyList();
        try {
            intercepts.annotations(); // triggers MirroredTypesException
            return Collections.emptyList();
        } catch (MirroredTypesException e) {
            return new ArrayList<>(e.getTypeMirrors());
        }
    }

    private boolean beanHasAnnotatedMethods(TypeElement bean, List<TypeMirror> targetAnnotations) {
        Types typeUtils = processingEnv.getTypeUtils();
        for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
            for (AnnotationMirror am : method.getAnnotationMirrors()) {
                for (TypeMirror target : targetAnnotations) {
                    if (typeUtils.isSameType(am.getAnnotationType(), target)) {
                        return true;
                    }
                }
            }
        }
        return false;
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

    private boolean hasAnnotation(Element element, String annotationFqn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(annotationFqn)) {
                return true;
            }
        }
        return false;
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
        return !hasAnnotation(enclosingClass, "summer.core.Component")
                && !hasAnnotation(enclosingClass, "summer.core.annotation.Configuration")
                && !hasAnnotation(enclosingClass, "summer.web.annotation.RestController");
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

    private void generateAotProxy(BeanDefinition bean) {
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(bean.typeElement).getQualifiedName().toString();
        String proxyClassName = bean.simpleName() + "$$AotProxy";

        TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (TypeMirror iface : bean.interfaces) {
            proxyBuilder.addSuperinterface(TypeName.get(iface));
        }

        proxyBuilder.addField(ClassName.get(bean.typeElement), "target", Modifier.PRIVATE, Modifier.FINAL);
        
        ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
        TypeName interceptorList = ParameterizedTypeName.get(ClassName.get(java.util.List.class), interceptorType);
        proxyBuilder.addField(interceptorList, "interceptors", Modifier.PRIVATE, Modifier.FINAL);

        // Constructor
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(bean.typeElement), "target")
                .addParameter(interceptorList, "interceptors")
                .addStatement("this.target = target")
                .addStatement("this.interceptors = interceptors")
                .build();
        proxyBuilder.addMethod(constructor);

        // Find all interface methods and generate them
        Map<String, ProxyMethod> uniqueMethods = new LinkedHashMap<>();
        for (TypeMirror iface : bean.interfaces) {
            TypeElement ifaceElement = asTypeElement(iface);
            if (ifaceElement != null) {
                collectMethods(ifaceElement, ifaceElement, uniqueMethods);
            }
        }

        int methodIndex = 0;

        for (ProxyMethod pm : uniqueMethods.values()) {
            boolean isIntercepted = shouldInterceptMethod(bean.typeElement, pm.method, bean.interceptors);
            
            String targetFieldName = null;
            String interfaceFieldName = null;

            if (isIntercepted) {
                targetFieldName = pm.method.getSimpleName().toString() + "_" + methodIndex + "_targetMethod";
                interfaceFieldName = pm.method.getSimpleName().toString() + "_" + methodIndex + "_interfaceMethod";
                methodIndex++;

                // Build MethodMetadata using anonymous inner class
                TypeSpec.Builder metadataTarget = TypeSpec.anonymousClassBuilder("")
                        .addSuperinterface(ClassName.get("summer.aop", "MethodMetadata"))
                        .addMethod(MethodSpec.methodBuilder("getName")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(String.class)
                                .addStatement("return $S", pm.method.getSimpleName().toString())
                                .build())
                        .addMethod(MethodSpec.methodBuilder("getDeclaringClass")
                                .addAnnotation(Override.class)
                                .addModifiers(Modifier.PUBLIC)
                                .returns(Class.class)
                                .addStatement("return $T.class", ClassName.get(bean.typeElement))
                                .build());

                MethodSpec.Builder isPresent = MethodSpec.methodBuilder("isAnnotationPresent")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean.class)
                        .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(java.lang.annotation.Annotation.class)), "cls");

                MethodSpec.Builder getAnn = MethodSpec.methodBuilder("getAnnotation")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addTypeVariable(TypeVariableName.get("T", java.lang.annotation.Annotation.class))
                        .returns(TypeVariableName.get("T"))
                        .addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "cls");

                ExecutableElement actualMethod = findMatchingMethod(bean.typeElement, pm.method);
                if (actualMethod != null) {
                    for (AnnotationMirror mirror : actualMethod.getAnnotationMirrors()) {
                        TypeElement annType = (TypeElement) mirror.getAnnotationType().asElement();
                        isPresent.beginControlFlow("if (cls == $T.class)", ClassName.get(annType));
                        isPresent.addStatement("return true");
                        isPresent.endControlFlow();
                        
                        getAnn.beginControlFlow("if (cls == $T.class)", ClassName.get(annType));
                        
                        TypeSpec.Builder annImpl = TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(ClassName.get(annType))
                                .addMethod(MethodSpec.methodBuilder("annotationType")
                                        .addAnnotation(Override.class)
                                        .addModifiers(Modifier.PUBLIC)
                                        .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(java.lang.annotation.Annotation.class)))
                                        .addStatement("return $T.class", ClassName.get(annType))
                                        .build());
                        
                        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
                            String attrName = entry.getKey().getSimpleName().toString();
                            Object val = entry.getValue().getValue();
                            String format = "return $L";
                            if (val instanceof String) {
                                format = "return $S";
                            } else if (val instanceof VariableElement) {
                                VariableElement ve = (VariableElement) val;
                                val = ClassName.get((TypeElement) ve.getEnclosingElement()) + "." + ve.getSimpleName();
                            } else if (val instanceof TypeMirror) {
                                val = TypeName.get((TypeMirror) val) + ".class";
                            } else if (val instanceof java.util.List) {
                                java.util.List<?> list = (java.util.List<?>) val;
                                StringBuilder arrayValues = new StringBuilder();
                                TypeMirror returnTm = entry.getKey().getReturnType();
                                TypeMirror erasedReturnTm = processingEnv.getTypeUtils().erasure(returnTm);
                                TypeName returnType = TypeName.get(erasedReturnTm);
                                arrayValues.append("new ").append(returnType.toString().replace("[]", "")).append("[]{");
                                for (int i = 0; i < list.size(); i++) {
                                    Object elem = list.get(i);
                                    if (elem instanceof javax.lang.model.element.AnnotationValue) {
                                        Object innerVal = ((javax.lang.model.element.AnnotationValue) elem).getValue();
                                        if (i > 0) arrayValues.append(", ");
                                        if (innerVal instanceof String) arrayValues.append("\"").append(innerVal).append("\"");
                                        else if (innerVal instanceof VariableElement) {
                                            VariableElement ve = (VariableElement) innerVal;
                                            arrayValues.append(ClassName.get((TypeElement) ve.getEnclosingElement())).append(".").append(ve.getSimpleName());
                                        } else if (innerVal instanceof TypeMirror) {
                                            // Handle erasure or simply raw class
                                            TypeMirror tm = (TypeMirror) innerVal;
                                            arrayValues.append(TypeName.get(processingEnv.getTypeUtils().erasure(tm))).append(".class");
                                        } else {
                                            arrayValues.append(innerVal);
                                        }
                                    }
                                }
                                arrayValues.append("}");
                                val = arrayValues.toString();
                            }
                            
                            annImpl.addMethod(MethodSpec.methodBuilder(attrName)
                                    .addModifiers(Modifier.PUBLIC)
                                    .returns(TypeName.get(entry.getKey().getReturnType()))
                                    .addStatement(format, val)
                                    .build());
                        }
                        getAnn.addStatement("return (T) $L", annImpl.build());
                        getAnn.endControlFlow();
                    }
                }
                isPresent.addStatement("return false");
                getAnn.addStatement("return null");

                metadataTarget.addMethod(isPresent.build());
                metadataTarget.addMethod(getAnn.build());

                proxyBuilder.addField(FieldSpec.builder(ClassName.get("summer.aop", "MethodMetadata"), targetFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", metadataTarget.build())
                        .build());
                
                proxyBuilder.addField(FieldSpec.builder(ClassName.get("summer.aop", "MethodMetadata"), interfaceFieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$L", metadataTarget.build())
                        .build());
            }

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(pm.method.getSimpleName().toString())
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.get(pm.method.getReturnType()));

            for (VariableElement param : pm.method.getParameters()) {
                methodBuilder.addParameter(TypeName.get(param.asType()), param.getSimpleName().toString());
            }

            StringBuilder argsCall = new StringBuilder();
            for (int j = 0; j < pm.method.getParameters().size(); j++) {
                if (j > 0) argsCall.append(", ");
                argsCall.append(pm.method.getParameters().get(j).getSimpleName().toString());
            }

            if (isIntercepted) {
                methodBuilder.beginControlFlow("try");
                if (pm.method.getParameters().isEmpty()) {
                    methodBuilder.addStatement("$T[] args = new $T[0]", Object.class, Object.class);
                } else {
                    methodBuilder.addStatement("$T[] args = new $T[]{$L}", Object.class, Object.class, argsCall.toString());
                }
                
                String lambdaCode;
                if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                    lambdaCode = "() -> { this.target." + pm.method.getSimpleName().toString() + "(" + argsCall.toString() + "); return null; }";
                } else {
                    lambdaCode = "() -> { return this.target." + pm.method.getSimpleName().toString() + "(" + argsCall.toString() + "); }";
                }

                methodBuilder.addStatement("$T ctx = new $T(this.target, $N, $N, args, this.interceptors, $L)",
                        ClassName.get("summer.aop", "AotInvocationContext"),
                        ClassName.get("summer.aop", "AotInvocationContext"),
                        targetFieldName,
                        interfaceFieldName,
                        lambdaCode);

                if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                    methodBuilder.addStatement("ctx.proceed()");
                } else {
                    methodBuilder.addStatement("Object result = ctx.proceed()");
                    TypeName returnType = TypeName.get(pm.method.getReturnType());
                    TypeName castType = returnType.isPrimitive() ? returnType.box() : returnType;
                    methodBuilder.addStatement("return ($T) result", castType);
                }

                methodBuilder.nextControlFlow("catch ($T t)", Throwable.class);
                methodBuilder.addStatement("throw sneakyThrow(t)");
                methodBuilder.endControlFlow();
            } else {
                if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                    methodBuilder.addStatement("this.target.$N($L)", pm.method.getSimpleName().toString(), argsCall.toString());
                } else {
                    methodBuilder.addStatement("return this.target.$N($L)", pm.method.getSimpleName().toString(), argsCall.toString());
                }
            }

            proxyBuilder.addMethod(methodBuilder.build());
        }

        // sneakyThrow
        MethodSpec sneakyThrow = MethodSpec.methodBuilder("sneakyThrow")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .addTypeVariable(TypeVariableName.get("T", Throwable.class))
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .returns(RuntimeException.class)
                .addParameter(Throwable.class, "t")
                .addException(TypeVariableName.get("T"))
                .addStatement("throw (T) t")
                .build();
        proxyBuilder.addMethod(sneakyThrow);

        JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).build();
        try {
            proxyFile.writeTo(processingEnv.getFiler());
            generatedNewTypesInThisRound = true;
        } catch (IOException e) {
            error("Failed to write AOP proxy for: " + proxyClassName, bean.typeElement);
        }
    }

    private void collectMethods(TypeElement interfaceElement, TypeElement originalInterface, Map<String, ProxyMethod> uniqueMethods) {
        for (Element e : interfaceElement.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) e;
                String sig = getMethodSignature(method);
                if (!uniqueMethods.containsKey(sig)) {
                    ProxyMethod pm = new ProxyMethod();
                    pm.method = method;
                    pm.declaringInterface = originalInterface;
                    uniqueMethods.put(sig, pm);
                }
            }
        }
        for (TypeMirror superI : interfaceElement.getInterfaces()) {
            TypeElement superEl = asTypeElement(superI);
            if (superEl != null) {
                collectMethods(superEl, originalInterface, uniqueMethods);
            }
        }
    }

    private String getMethodSignature(ExecutableElement method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getSimpleName().toString()).append("(");
        for (VariableElement p : method.getParameters()) {
            sb.append(processingEnv.getTypeUtils().erasure(p.asType()).toString()).append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    private boolean shouldInterceptMethod(TypeElement beanClass, ExecutableElement interfaceMethod, List<BeanDefinition> interceptorBeans) {
        ExecutableElement targetMethod = findMatchingMethod(beanClass, interfaceMethod);
        if (targetMethod == null) return false;

        // 1. Explicitly annotated with @Intercepted
        if (hasAnnotation(targetMethod, "summer.aop.Intercepted")) {
            return true;
        }

        // 2. Annotated with any trigger annotation declared by the active interceptors
        for (BeanDefinition interceptor : interceptorBeans) {
            List<TypeMirror> targetAnnotations = getInterceptsAnnotations(interceptor.typeElement);
            for (AnnotationMirror am : targetMethod.getAnnotationMirrors()) {
                for (TypeMirror targetAnn : targetAnnotations) {
                    if (processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), targetAnn)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ExecutableElement findMatchingMethod(TypeElement beanClass, ExecutableElement interfaceMethod) {
        Types typeUtils = processingEnv.getTypeUtils();
        String name = interfaceMethod.getSimpleName().toString();
        List<TypeMirror> interfaceParamTypes = interfaceMethod.getParameters().stream().map(VariableElement::asType).toList();

        for (ExecutableElement method : ElementFilter.methodsIn(beanClass.getEnclosedElements())) {
            if (method.getSimpleName().toString().equals(name)) {
                List<TypeMirror> targetParamTypes = method.getParameters().stream().map(VariableElement::asType).toList();
                if (targetParamTypes.size() == interfaceParamTypes.size()) {
                    boolean match = true;
                    for (int i = 0; i < targetParamTypes.size(); i++) {
                        if (!typeUtils.isSameType(
                                typeUtils.erasure(targetParamTypes.get(i)),
                                typeUtils.erasure(interfaceParamTypes.get(i)))) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static class ProxyMethod {
        ExecutableElement method;
        TypeElement declaringInterface;
    }



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
                AnnotationMirror condMirror = getAnnotationMirror(bean.typeElement,
                        "summer.core.annotation.ConditionalOnBean");
                if (condMirror == null) continue;

                // Get the value() from the annotation
                Object value = getAnnotationClassValue(condMirror);
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

    private Object getAnnotationClassValue(AnnotationMirror mirror) {
        for (var entry : processingEnv.getElementUtils()
                .getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("value")) {
                return entry.getValue().getValue();
            }
        }
        return null;
    }

    /**
     * Resolves @Replaces annotations: removes replaced @Configuration classes
     * and their @Bean factory products from allBeans.
     */
    private void resolveReplacements() {
        Map<TypeElement, TypeElement> replacementMap = new HashMap<>(); // replaced -> replacement

        for (BeanDefinition bean : allBeans) {
            if (bean.kind != BeanDefinition.Kind.CONFIGURATION) continue;
            if (!hasAnnotation(bean.typeElement, "summer.core.annotation.Replaces")) continue;

            List<TypeMirror> targets = getAnnotationClassListValue(bean.typeElement, "summer.core.annotation.Replaces");
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

    private void generateRouteAdapter(List<BeanDefinition> beans) {
        TypeElement restControllerType = processingEnv.getElementUtils().getTypeElement("summer.web.annotation.RestController");
        if (restControllerType == null) return; // summer-web not on classpath

        List<BeanDefinition> controllers = beans.stream()
                .filter(b -> hasAnnotation(b.typeElement, "summer.web.annotation.RestController"))
                .toList();

        List<BeanDefinition> componentsWithExceptionHandlers = new ArrayList<>();
        for (BeanDefinition b : beans) {
            boolean hasHandler = ElementFilter.methodsIn(b.typeElement.getEnclosedElements()).stream()
                    .anyMatch(m -> hasAnnotation(m, "summer.web.annotation.ExceptionHandler"));
            if (hasHandler) {
                componentsWithExceptionHandlers.add(b);
            }
        }

        if (controllers.isEmpty() && componentsWithExceptionHandlers.isEmpty()) {
            return; // No routes or exception handlers to register
        }

        TypeSpec.Builder adapterBuilder = TypeSpec.classBuilder("GeneratedAnnotationRouterAdapter")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("summer.web", "RouteRegistrar"));

        // Logger field
        adapterBuilder.addField(
                FieldSpec.builder(ClassName.get("org.slf4j", "Logger"), "log", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$T.getLogger(GeneratedAnnotationRouterAdapter.class)", ClassName.get("org.slf4j", "LoggerFactory"))
                        .build()
        );

        // Context field and constructor
        ClassName contextClass = ClassName.get("summer.core.aot", "GeneratedAotContext");
        adapterBuilder.addField(contextClass, "context", Modifier.PRIVATE, Modifier.FINAL);
        adapterBuilder.addField(ClassName.get("summer.web", "Router"), "router", Modifier.PRIVATE, Modifier.FINAL);
        adapterBuilder.addField(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry", Modifier.PRIVATE, Modifier.FINAL);

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("summer.web", "Router"), "router")
                .addParameter(ClassName.get("summer.core", "ApplicationContext"), "context")
                .addParameter(ClassName.get("summer.web", "ExceptionRegistry"), "exceptionRegistry")
                .addStatement("this.context = (GeneratedAotContext) context")
                .addStatement("this.router = router")
                .addStatement("this.exceptionRegistry = exceptionRegistry")
                .build();
        adapterBuilder.addMethod(constructor);

        // registerControllers() method
        MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("registerControllers")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC);

        Types types = processingEnv.getTypeUtils();
        TypeMirror throwableType = processingEnv.getElementUtils().getTypeElement("java.lang.Throwable").asType();

        // Register controller routes
        for (BeanDefinition controller : controllers) {
            String basePath = getAnnotationStringValue(controller.typeElement, "summer.web.annotation.RestController");
            ClassName controllerClass = ClassName.get(controller.typeElement);

            for (ExecutableElement method : ElementFilter.methodsIn(controller.typeElement.getEnclosedElements())) {
                String httpMethod = null;
                String methodPath = null;

                if (hasAnnotation(method, "summer.web.annotation.Get")) {
                    httpMethod = "GET";
                    methodPath = getAnnotationStringValue(method, "summer.web.annotation.Get");
                } else if (hasAnnotation(method, "summer.web.annotation.Post")) {
                    httpMethod = "POST";
                    methodPath = getAnnotationStringValue(method, "summer.web.annotation.Post");
                } else if (hasAnnotation(method, "summer.web.annotation.Put")) {
                    httpMethod = "PUT";
                    methodPath = getAnnotationStringValue(method, "summer.web.annotation.Put");
                } else if (hasAnnotation(method, "summer.web.annotation.Delete")) {
                    httpMethod = "DELETE";
                    methodPath = getAnnotationStringValue(method, "summer.web.annotation.Delete");
                }

                if (httpMethod != null) {
                    String combinedPath = combinePaths(basePath, methodPath);
                    CodeBlock args = buildMethodCallArgs(method, types, throwableType);

                    // Build lambda body
                    CodeBlock.Builder lambdaBody = CodeBlock.builder();
                    lambdaBody.add("$T controller = this.context.getBean($T.class);\n", controllerClass, controllerClass);
                    if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                        lambdaBody.add("controller.$N($L);\n", method.getSimpleName().toString(), args);
                        lambdaBody.add("return \"\";\n");
                    } else {
                        lambdaBody.add("return controller.$N($L);\n", method.getSimpleName().toString(), args);
                    }

                    // Define lambda Handler
                    registerMethod.addCode("{\n");
                    registerMethod.addStatement("$T handler = ctx -> {\n$L}",
                            ClassName.get("summer.web", "Handler"),
                            lambdaBody.build());

                    // Apply middlewares in reverse order
                    List<TypeMirror> classMiddlewares = getAnnotationClassListValue(controller.typeElement, "summer.web.annotation.Use");
                    List<TypeMirror> methodMiddlewares = getAnnotationClassListValue(method, "summer.web.annotation.Use");
                    List<TypeMirror> allMiddlewares = new ArrayList<>();
                    allMiddlewares.addAll(classMiddlewares);
                    allMiddlewares.addAll(methodMiddlewares);
                    Collections.reverse(allMiddlewares);

                    for (TypeMirror mw : allMiddlewares) {
                        registerMethod.addStatement("handler = this.context.getBean($T.class).apply(handler)", TypeName.get(mw));
                    }

                    registerMethod.addStatement("this.router.register($S, $S, handler)", httpMethod, combinedPath);
                    registerMethod.addStatement("log.info($S, $S + $S)",
                            "Route registered (Static AOT): {} {}", httpMethod, combinedPath);
                    registerMethod.addCode("}\n");
                }
            }
        }

        // Register exception handlers
        for (BeanDefinition component : componentsWithExceptionHandlers) {
            ClassName componentClass = ClassName.get(component.typeElement);

            for (ExecutableElement method : ElementFilter.methodsIn(component.typeElement.getEnclosedElements())) {
                if (hasAnnotation(method, "summer.web.annotation.ExceptionHandler")) {
                    List<TypeMirror> exceptionClasses = getAnnotationClassListValue(method, "summer.web.annotation.ExceptionHandler");
                    CodeBlock args = buildMethodCallArgs(method, types, throwableType);

                    for (TypeMirror exc : exceptionClasses) {
                        CodeBlock.Builder lambdaBody = CodeBlock.builder();
                        lambdaBody.add("$T bean = this.context.getBean($T.class);\n", componentClass, componentClass);
                        if (method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                            lambdaBody.add("bean.$N($L);\n", method.getSimpleName().toString(), args);
                            lambdaBody.add("return \"\";\n");
                        } else {
                            lambdaBody.add("return bean.$N($L);\n", method.getSimpleName().toString(), args);
                        }

                        registerMethod.addCode("{\n");
                        registerMethod.addStatement("$T handler = ctx -> {\n$L}",
                                ClassName.get("summer.web", "Handler"),
                                lambdaBody.build());
                        registerMethod.addStatement("this.exceptionRegistry.register($T.class, handler)", TypeName.get(exc));
                        registerMethod.addStatement("log.info($S, $T.class.getSimpleName())",
                                "Exception Handler registered (Static AOT): {}", TypeName.get(exc));
                        registerMethod.addCode("}\n");
                    }
                }
            }
        }

        adapterBuilder.addMethod(registerMethod.build());

        JavaFile javaFile = JavaFile.builder("summer.core.aot", adapterBuilder.build())
                .indent("    ")
                .build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
            generatedNewTypesInThisRound = true;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "[Summer AOT] Generated summer.core.aot.GeneratedAnnotationRouterAdapter");
        } catch (IOException e) {
            error("Failed to write GeneratedAnnotationRouterAdapter: " + e.getMessage(), null);
        }
    }

    private String getAnnotationStringValue(Element element, String annotationFqn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(annotationFqn)) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> values = 
                        processingEnv.getElementUtils().getElementValuesWithDefaults(am);
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        return entry.getValue().getValue().toString();
                    }
                }
            }
        }
        return "";
    }

    private List<TypeMirror> getAnnotationClassListValue(Element element, String annotationFqn) {
        List<TypeMirror> result = new ArrayList<>();
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(annotationFqn)) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> values = 
                        processingEnv.getElementUtils().getElementValuesWithDefaults(am);
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        Object val = entry.getValue().getValue();
                        if (val instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof AnnotationValue av) {
                                    Object innerVal = av.getValue();
                                    if (innerVal instanceof TypeMirror tm) {
                                        result.add(tm);
                                    }
                                }
                            }
                        } else if (val instanceof TypeMirror tm) {
                            result.add(tm);
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<String> getAnnotationStringArrayValue(AnnotationMirror am, String paramName) {
        List<String> result = new ArrayList<>();
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = 
                processingEnv.getElementUtils().getElementValuesWithDefaults(am);
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(paramName)) {
                Object val = entry.getValue().getValue();
                if (val instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof AnnotationValue av) {
                            result.add(av.getValue().toString().replace("\"", ""));
                        }
                    }
                } else if (val instanceof String s) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    private AnnotationMirror getAnnotationMirror(Element element, String annotationFqn) {
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (am.getAnnotationType().toString().equals(annotationFqn)) {
                return am;
            }
        }
        return null;
    }

    private CodeBlock buildMethodCallArgs(ExecutableElement method, Types types, TypeMirror throwableType) {
        CodeBlock.Builder builder = CodeBlock.builder();
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) builder.add(", ");
            VariableElement param = params.get(i);
            TypeMirror paramType = param.asType();
            
            if (paramType.toString().equals("summer.web.WebContext")) {
                builder.add("ctx");
            } else if (paramType.toString().equals("summer.web.Request")) {
                builder.add("ctx.request()");
            } else if (paramType.toString().equals("summer.web.Response")) {
                builder.add("ctx.response()");
            } else if (hasAnnotation(param, "summer.web.annotation.PathParam")) {
                String val = getAnnotationStringValue(param, "summer.web.annotation.PathParam");
                builder.add("ctx.request().pathParam($S)", val);
            } else if (types.isAssignable(paramType, throwableType)) {
                builder.add("($T) ctx.request().getAttribute(\"last_exception\")", TypeName.get(paramType));
            } else {
                if (hasAnnotation(param, "summer.web.annotation.Valid")) {
                    builder.add("ctx.validatedBody($T.class)", TypeName.get(types.erasure(paramType)));
                } else {
                    builder.add("ctx.body($T.class)", TypeName.get(types.erasure(paramType)));
                }
            }
        }
        return builder.build();
    }

    private String combinePaths(String basePath, String methodPath) {
        if (basePath == null) basePath = "";
        if (methodPath == null) methodPath = "";
        if (basePath.isEmpty()) return normalizePath(methodPath);
        if (methodPath.isEmpty()) return normalizePath(basePath);
        String normalizedBase = normalizePath(basePath);
        String normalizedMethod = normalizePath(methodPath);
        if (normalizedBase.endsWith("/") && normalizedMethod.startsWith("/")) {
            return normalizedBase + normalizedMethod.substring(1);
        } else if (!normalizedBase.endsWith("/") && !normalizedMethod.startsWith("/")) {
            return normalizedBase + "/" + normalizedMethod;
        } else {
            return normalizedBase + normalizedMethod;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) return "/" + path;
        return path;
    }
}
