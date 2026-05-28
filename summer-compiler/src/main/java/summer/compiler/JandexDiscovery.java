package summer.compiler;

import org.jboss.jandex.*;
import summer.core.annotation.Configuration;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Jandex index loading and cross-module bean discovery.
 * Extracted from SummerProcessor to separate discovery concerns from collection.
 */
final class JandexDiscovery {

    private JandexDiscovery() {}

    /**
     * Callback interface for registering discovered beans.
     * Implemented by SummerProcessor to delegate to its collect methods.
     */
    interface BeanCollector {
        void collectComponent(TypeElement typeElement);
        void collectConfiguration(TypeElement typeElement);
        boolean alreadyCollected(TypeElement typeElement);
    }

    /**
     * Loads all pre-built Jandex indexes from dependency JARs on the processor classpath.
     * Returns a CompositeIndex merging all discovered META-INF/jandex.idx files.
     */
    static CompositeIndex loadIndex() throws IOException {
        List<IndexView> indexes = new ArrayList<>();
        ClassLoader cl = JandexDiscovery.class.getClassLoader();
        Enumeration<URL> urls = cl.getResources("META-INF/jandex.idx");
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            InputStream is = url.openStream();
            indexes.add(new IndexReader(is).read());
            is.close();
        }
        return CompositeIndex.create(indexes);
    }

    /**
     * Discovers framework beans from Jandex indexes: @Component, @Configuration,
     * and meta-annotated components (@RestController, @GlobalMiddleware, etc.).
     */
    static void discoverFrameworkBeans(List<BeanDefinition> allBeans, CompositeIndex index,
                                        ProcessingEnvironment processingEnv, BeanCollector collector) {
        DotName componentDot = DotName.createSimple("summer.core.annotation.Component");
        DotName configDot = DotName.createSimple("summer.core.annotation.Configuration");

        // Discover @Component-annotated classes from dependency indexes
        for (AnnotationInstance ai : index.getAnnotations(componentDot)) {
            if (ai.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo ci = ai.target().asClass();
            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te != null && !collector.alreadyCollected(te)) {
                collector.collectComponent(te);
            }
        }

        // Discover @Configuration classes (meta-annotated with @Component, but need
        // collectConfiguration to also pick up their @Bean methods)
        for (AnnotationInstance ai : index.getAnnotations(configDot)) {
            if (ai.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo ci = ai.target().asClass();
            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te != null && !collector.alreadyCollected(te)) {
                collector.collectConfiguration(te);
            }
        }

        // Discover meta-annotated components: @RestController, @GlobalMiddleware, etc.
        for (AnnotationInstance metaAnn : index.getAnnotations(componentDot)) {
            if (metaAnn.target().kind() != AnnotationTarget.Kind.CLASS) continue;
            ClassInfo annotatedClass = metaAnn.target().asClass();
            if (!annotatedClass.isAnnotation()) {
                continue;
            }
            DotName metaAnnotationName = annotatedClass.name();
            for (AnnotationInstance usage : index.getAnnotations(metaAnnotationName)) {
                if (usage.target().kind() != AnnotationTarget.Kind.CLASS) continue;
                ClassInfo userClass = usage.target().asClass();
                TypeElement te = processingEnv.getElementUtils().getTypeElement(userClass.name().toString());
                if (te != null && !collector.alreadyCollected(te)) {
                    collector.collectComponent(te);
                }
            }
        }
    }

    /**
     * Discovers MethodInterceptor beans with @Intercepts from Jandex indexes.
     * These are needed for AOP proxy wrapping but aren't direct constructor dependencies.
     */
    static void discoverInterceptorBeans(List<BeanDefinition> allBeans, CompositeIndex index,
                                          ProcessingEnvironment processingEnv, BeanCollector collector) {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeElement miType = processingEnv.getElementUtils()
                .getTypeElement("summer.aop.MethodInterceptor");
        if (miType == null) return;

        boolean hasInterceptors = allBeans.stream().anyMatch(b ->
                typeUtils.isAssignable(
                        typeUtils.erasure(b.typeElement.asType()),
                        typeUtils.erasure(miType.asType())));
        if (hasInterceptors) return;

        DotName miDot = DotName.createSimple("summer.aop.MethodInterceptor");
        DotName interceptsDot = DotName.createSimple("summer.aop.Intercepts");

        for (ClassInfo ci : index.getAllKnownImplementors(miDot)) {
            AnnotationInstance intercepts = ci.annotation(interceptsDot);
            if (intercepts == null) continue;

            TypeElement te = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (te == null) continue;
            if (collector.alreadyCollected(te)) continue;

            String targetAnnotationFqn = null;
            org.jboss.jandex.AnnotationValue annValue = intercepts.value("annotations");
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
                                .anyMatch(m -> AnnotationHelper.hasAnnotation(m, finalTargetFqn)));

                if (hasTarget && AnnotationHelper.hasAnnotation(te, "summer.core.Component")) {
                    collector.collectComponent(te);
                }
            }
        }
    }

    /**
     * For an interface type, tries to discover a concrete @Component implementation
     * from the Jandex index.
     */
    static boolean tryDiscoverImplementation(TypeElement interfaceElement, List<BeanDefinition> allBeans,
                                              CompositeIndex index, ProcessingEnvironment processingEnv,
                                              BeanCollector collector) {
        DotName ifaceDot = DotName.createSimple(interfaceElement.getQualifiedName().toString());

        for (ClassInfo ci : index.getAllKnownImplementors(ifaceDot)) {
            if (ci.isAbstract() || ci.isInterface()) continue;

            TypeElement implElement = processingEnv.getElementUtils().getTypeElement(ci.name().toString());
            if (implElement != null && tryCollectFromClasspath(implElement, allBeans, processingEnv, collector)) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each bean's constructor/producer params, if the param type isn't among
     * collected beans, try to find it on the classpath and auto-register it.
     */
    static void discoverTransitiveDependencies(List<BeanDefinition> allBeans, CompositeIndex index,
                                                ProcessingEnvironment processingEnv, BeanCollector collector) {
        Types typeUtils = processingEnv.getTypeUtils();
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
                TypeElement paramElement = asTypeElement(paramType, processingEnv);
                if (paramElement == null) continue;

                if (isBeanSatisfied(paramElement, allBeans, processingEnv)) continue;

                if (tryCollectFromClasspath(paramElement, allBeans, processingEnv, collector)) {
                    changed = true;
                    continue;
                }

                if (paramElement.getKind() == ElementKind.INTERFACE) {
                    if (tryDiscoverImplementation(paramElement, allBeans, index, processingEnv, collector)) {
                        changed = true;
                    }
                }
            }
        }
    }

    // --- Private helpers ---

    private static boolean isBeanSatisfied(TypeElement typeElement, List<BeanDefinition> allBeans,
                                            ProcessingEnvironment processingEnv) {
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

    private static boolean tryCollectFromClasspath(TypeElement element, List<BeanDefinition> allBeans,
                                                    ProcessingEnvironment processingEnv, BeanCollector collector) {
        if (collector.alreadyCollected(element)) return false;

        if (AnnotationHelper.hasAnnotation(element, "summer.core.Component")
                || AnnotationHelper.hasAnnotation(element, "summer.core.annotation.Configuration")
                || AnnotationHelper.hasAnnotation(element, "summer.web.annotation.RestController")
                || AnnotationHelper.hasAnnotation(element, "summer.web.annotation.GlobalMiddleware")) {

            if (element.getAnnotation(summer.core.annotation.Configuration.class) != null) {
                collector.collectConfiguration(element);
            } else {
                collector.collectComponent(element);
            }
            return true;
        }
        return false;
    }

    private static TypeElement asTypeElement(TypeMirror typeMirror, ProcessingEnvironment processingEnv) {
        javax.lang.model.element.Element element = processingEnv.getTypeUtils().asElement(typeMirror);
        return (element instanceof TypeElement te) ? te : null;
    }
}
