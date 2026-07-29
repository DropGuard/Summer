mport com.github.dropguard.summer.core.Internal;
package com.github.dropguard.summer.core.bean;

mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
import java.util.HashMap;
mport com.github.dropguard.summer.core.Internal;
import java.util.HashSet;
mport com.github.dropguard.summer.core.Internal;
import java.util.List;
mport com.github.dropguard.summer.core.Internal;
import java.util.Map;
mport com.github.dropguard.summer.core.Internal;
import java.util.Set;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.AnnotationInstance;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.ClassInfo;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.DotName;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.IndexView;
mport com.github.dropguard.summer.core.Internal;
import org.jboss.jandex.MethodInfo;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
@Internal
/**
mport com.github.dropguard.summer.core.Internal;
 * Enriches discovered bean definitions with constructor params, interface names, route metadata,
mport com.github.dropguard.summer.core.Internal;
 * and AOP binding information.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>This is the enrichment phase of discovery — runs after the candidate set is enumerated (from a
mport com.github.dropguard.summer.core.Internal;
 * {@link BeanDeployment}) but before condition evaluation ({@code @ConditionalOnBean}/{@Replaces})
mport com.github.dropguard.summer.core.Internal;
 * and dependency resolution. It only reads Jandex metadata into {@link BeanDefinition} fields; it
mport com.github.dropguard.summer.core.Internal;
 * never removes or reorders beans, so it is safe to run on the shared discovery output that both
mport com.github.dropguard.summer.core.Internal;
 * the Runtime and AOT engines consume.
mport com.github.dropguard.summer.core.Internal;
 *
mport com.github.dropguard.summer.core.Internal;
 * <p>Lives in {@code com.github.dropguard.summer.core} (not {@code
mport com.github.dropguard.summer.core.Internal;
 * com.github.dropguard.summer.aot}) so the unified {@link Discovery} can call it without core
mport com.github.dropguard.summer.core.Internal;
 * depending on the AOT module.
mport com.github.dropguard.summer.core.Internal;
 */
mport com.github.dropguard.summer.core.Internal;
public final class BeanEnrichment {
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final DotName REST_CONTROLLER_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.web.annotation.RestController");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName PATH_PARAM_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.web.annotation.PathParam");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName QUERY_PARAM_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.web.annotation.QueryParam");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName VALID_DOT = DotName.createSimple("jakarta.validation.Valid");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName INTERCEPTOR_BINDING_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.aop.InterceptorBinding");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName INTERCEPTOR_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.aop.Interceptor");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final Map<DotName, String> HTTP_ANNOTATIONS =
mport com.github.dropguard.summer.core.Internal;
            Map.of(
mport com.github.dropguard.summer.core.Internal;
                    DotName.createSimple("com.github.dropguard.summer.web.annotation.Get"), "GET",
mport com.github.dropguard.summer.core.Internal;
                    DotName.createSimple("com.github.dropguard.summer.web.annotation.Post"), "POST",
mport com.github.dropguard.summer.core.Internal;
                    DotName.createSimple("com.github.dropguard.summer.web.annotation.Put"), "PUT",
mport com.github.dropguard.summer.core.Internal;
                    DotName.createSimple("com.github.dropguard.summer.web.annotation.Delete"),
mport com.github.dropguard.summer.core.Internal;
                            "DELETE");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private static final DotName EXCEPTION_HANDLER_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.web.annotation.ExceptionHandler");
mport com.github.dropguard.summer.core.Internal;
    private static final DotName CONDITIONAL_ON_BEAN_DOT =
mport com.github.dropguard.summer.core.Internal;
            DotName.createSimple("com.github.dropguard.summer.core.annotation.ConditionalOnBean");
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private final IndexView index;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    public BeanEnrichment(IndexView index) {
mport com.github.dropguard.summer.core.Internal;
        this.index = index;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    /**
mport com.github.dropguard.summer.core.Internal;
     * Enriches beans with constructor params, interface names, route metadata, and AOP bindings.
mport com.github.dropguard.summer.core.Internal;
     */
mport com.github.dropguard.summer.core.Internal;
    public void enrich(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean instanceof ConfigPropertiesBean) continue;
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
mport com.github.dropguard.summer.core.Internal;
            if (ci != null) {
mport com.github.dropguard.summer.core.Internal;
                if (!bean.isFactoryMethod()) {
mport com.github.dropguard.summer.core.Internal;
                    collectConstructorParams(bean, ci);
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                collectExceptionHandlers(bean, ci);
mport com.github.dropguard.summer.core.Internal;
                collectConditions(bean, ci);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        collectRouteMetadata(beans);
mport com.github.dropguard.summer.core.Internal;
        detectAopBindings(beans);
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Constructor Params ────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void collectConstructorParams(BeanDefinition bean, ClassInfo ci) {
mport com.github.dropguard.summer.core.Internal;
        List<MethodInfo> publicCtors =
mport com.github.dropguard.summer.core.Internal;
                ci.methods().stream()
mport com.github.dropguard.summer.core.Internal;
                        .filter(m -> m.name().equals("<init>") && (m.flags() & 0x0001) != 0)
mport com.github.dropguard.summer.core.Internal;
                        .toList();
mport com.github.dropguard.summer.core.Internal;
        if (publicCtors.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
mport com.github.dropguard.summer.core.Internal;
                    "Component "
mport com.github.dropguard.summer.core.Internal;
                            + bean.qualifiedName
mport com.github.dropguard.summer.core.Internal;
                            + " must have exactly ONE public constructor. Found: 0");
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        if (publicCtors.size() > 1) {
mport com.github.dropguard.summer.core.Internal;
            throw new com.github.dropguard.summer.core.exception.BeanCreationException(
mport com.github.dropguard.summer.core.Internal;
                    "Component "
mport com.github.dropguard.summer.core.Internal;
                            + bean.qualifiedName
mport com.github.dropguard.summer.core.Internal;
                            + " must have exactly ONE public constructor. Found: "
mport com.github.dropguard.summer.core.Internal;
                            + publicCtors.size());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        MethodInfo ctor = publicCtors.get(0);
mport com.github.dropguard.summer.core.Internal;
        for (int i = 0; i < ctor.parametersCount(); i++) {
mport com.github.dropguard.summer.core.Internal;
            org.jboss.jandex.Type paramType = ctor.parameterType(i);
mport com.github.dropguard.summer.core.Internal;
            if (paramType.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
mport com.github.dropguard.summer.core.Internal;
                org.jboss.jandex.ParameterizedType pt = paramType.asParameterizedType();
mport com.github.dropguard.summer.core.Internal;
                if (pt.name().toString().equals("java.util.List") && pt.arguments().size() == 1) {
mport com.github.dropguard.summer.core.Internal;
                    org.jboss.jandex.Type elementTypeObj = pt.arguments().get(0);
mport com.github.dropguard.summer.core.Internal;
                    if (elementTypeObj.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
mport com.github.dropguard.summer.core.Internal;
                        throw new com.github.dropguard.summer.core.exception
mport com.github.dropguard.summer.core.Internal;
                                .UnsupportedInjectionException(
mport com.github.dropguard.summer.core.Internal;
                                "Nested generic type injection is not supported: List<"
mport com.github.dropguard.summer.core.Internal;
                                        + elementTypeObj.toString()
mport com.github.dropguard.summer.core.Internal;
                                        + "> in "
mport com.github.dropguard.summer.core.Internal;
                                        + bean.qualifiedName);
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                    bean.addParameter("java.util.List<" + elementTypeObj.name().toString() + ">");
mport com.github.dropguard.summer.core.Internal;
                    continue;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            bean.addParameter(paramType.name().toString());
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── Route Metadata ────────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void collectRouteMetadata(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean instanceof ConfigPropertiesBean) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
mport com.github.dropguard.summer.core.Internal;
            if (ci == null || !ci.hasAnnotation(REST_CONTROLLER_DOT)) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            String basePath = extractBasePath(ci);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            for (MethodInfo method : ci.methods()) {
mport com.github.dropguard.summer.core.Internal;
                String httpMethod = resolveHttpMethod(method);
mport com.github.dropguard.summer.core.Internal;
                if (httpMethod == null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                String methodPath = extractMethodPath(method);
mport com.github.dropguard.summer.core.Internal;
                String fullPath = combinePaths(basePath, methodPath);
mport com.github.dropguard.summer.core.Internal;
                String returnType = method.returnType().name().toString();
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                RouteInfo route =
mport com.github.dropguard.summer.core.Internal;
                        new RouteInfo(
mport com.github.dropguard.summer.core.Internal;
                                httpMethod,
mport com.github.dropguard.summer.core.Internal;
                                fullPath,
mport com.github.dropguard.summer.core.Internal;
                                bean.qualifiedName,
mport com.github.dropguard.summer.core.Internal;
                                method.name(),
mport com.github.dropguard.summer.core.Internal;
                                returnType);
mport com.github.dropguard.summer.core.Internal;
                collectParameters(method, route);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                // Enforce Gin-style contract: first parameter MUST be HttpContext
mport com.github.dropguard.summer.core.Internal;
                var params = method.parameters();
mport com.github.dropguard.summer.core.Internal;
                if (params.isEmpty()
mport com.github.dropguard.summer.core.Internal;
                        || !params.get(0)
mport com.github.dropguard.summer.core.Internal;
                                .type()
mport com.github.dropguard.summer.core.Internal;
                                .name()
mport com.github.dropguard.summer.core.Internal;
                                .toString()
mport com.github.dropguard.summer.core.Internal;
                                .equals("com.github.dropguard.summer.web.HttpContext")) {
mport com.github.dropguard.summer.core.Internal;
                    throw new IllegalStateException(
mport com.github.dropguard.summer.core.Internal;
                            bean.qualifiedName
mport com.github.dropguard.summer.core.Internal;
                                    + "."
mport com.github.dropguard.summer.core.Internal;
                                    + method.name()
mport com.github.dropguard.summer.core.Internal;
                                    + "() must declare HttpContext as its first parameter. "
mport com.github.dropguard.summer.core.Internal;
                                    + "All controller methods follow the Gin pattern: "
mport com.github.dropguard.summer.core.Internal;
                                    + "first arg is always the context.");
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
                bean.routes.add(route);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String extractBasePath(ClassInfo ci) {
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance ann = ci.annotation(REST_CONTROLLER_DOT);
mport com.github.dropguard.summer.core.Internal;
        return (ann != null && ann.value() != null) ? ann.value().asString() : "";
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String resolveHttpMethod(MethodInfo method) {
mport com.github.dropguard.summer.core.Internal;
        for (var entry : HTTP_ANNOTATIONS.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
            if (method.hasAnnotation(entry.getKey())) return entry.getValue();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return null;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String extractMethodPath(MethodInfo method) {
mport com.github.dropguard.summer.core.Internal;
        for (DotName annotation : HTTP_ANNOTATIONS.keySet()) {
mport com.github.dropguard.summer.core.Internal;
            if (method.hasAnnotation(annotation)) {
mport com.github.dropguard.summer.core.Internal;
                AnnotationInstance ann = method.annotation(annotation);
mport com.github.dropguard.summer.core.Internal;
                return (ann != null && ann.value() != null) ? ann.value().asString() : "";
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return "";
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private boolean isScrollRequest(String paramType) {
mport com.github.dropguard.summer.core.Internal;
        if (paramType.equals("com.github.dropguard.summer.web.ScrollRequest")
mport com.github.dropguard.summer.core.Internal;
                || paramType.equals(
mport com.github.dropguard.summer.core.Internal;
                        "com.github.dropguard.summer.realworld.common.LimitOffsetPageable")
mport com.github.dropguard.summer.core.Internal;
                || paramType.equals("com.github.dropguard.summer.twitter.common.CursorPageable")) {
mport com.github.dropguard.summer.core.Internal;
            return true;
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        ClassInfo ci = index.getClassByName(DotName.createSimple(paramType));
mport com.github.dropguard.summer.core.Internal;
        if (ci != null) {
mport com.github.dropguard.summer.core.Internal;
            for (DotName iface : ci.interfaceNames()) {
mport com.github.dropguard.summer.core.Internal;
                if (isScrollRequest(iface.toString())) {
mport com.github.dropguard.summer.core.Internal;
                    return true;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (ci.superName() != null && !ci.superName().toString().equals("java.lang.Object")) {
mport com.github.dropguard.summer.core.Internal;
                return isScrollRequest(ci.superName().toString());
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
        return false;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void collectParameters(MethodInfo method, RouteInfo route) {
mport com.github.dropguard.summer.core.Internal;
        for (org.jboss.jandex.MethodParameterInfo param : method.parameters()) {
mport com.github.dropguard.summer.core.Internal;
            String paramName = param.name();
mport com.github.dropguard.summer.core.Internal;
            String paramType = param.type().name().toString();
mport com.github.dropguard.summer.core.Internal;
            boolean hasValid = param.hasAnnotation(VALID_DOT);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            if (param.hasAnnotation(PATH_PARAM_DOT)) {
mport com.github.dropguard.summer.core.Internal;
                String bindingName = extractBindingName(param, PATH_PARAM_DOT, paramName);
mport com.github.dropguard.summer.core.Internal;
                route.params.add(
mport com.github.dropguard.summer.core.Internal;
                        new RouteInfo.ParamInfo(
mport com.github.dropguard.summer.core.Internal;
                                paramName,
mport com.github.dropguard.summer.core.Internal;
                                bindingName,
mport com.github.dropguard.summer.core.Internal;
                                paramType,
mport com.github.dropguard.summer.core.Internal;
                                RouteInfo.ParamBinding.PATH,
mport com.github.dropguard.summer.core.Internal;
                                hasValid));
mport com.github.dropguard.summer.core.Internal;
            } else if (param.hasAnnotation(QUERY_PARAM_DOT)) {
mport com.github.dropguard.summer.core.Internal;
                String bindingName = extractBindingName(param, QUERY_PARAM_DOT, paramName);
mport com.github.dropguard.summer.core.Internal;
                route.params.add(
mport com.github.dropguard.summer.core.Internal;
                        new RouteInfo.ParamInfo(
mport com.github.dropguard.summer.core.Internal;
                                paramName,
mport com.github.dropguard.summer.core.Internal;
                                bindingName,
mport com.github.dropguard.summer.core.Internal;
                                paramType,
mport com.github.dropguard.summer.core.Internal;
                                RouteInfo.ParamBinding.QUERY,
mport com.github.dropguard.summer.core.Internal;
                                hasValid));
mport com.github.dropguard.summer.core.Internal;
            } else if (isScrollRequest(paramType)) {
mport com.github.dropguard.summer.core.Internal;
                route.params.add(
mport com.github.dropguard.summer.core.Internal;
                        new RouteInfo.ParamInfo(
mport com.github.dropguard.summer.core.Internal;
                                paramName, "", paramType, RouteInfo.ParamBinding.PAGEABLE, false));
mport com.github.dropguard.summer.core.Internal;
            } else if (!paramType.equals("com.github.dropguard.summer.web.WebContext")
mport com.github.dropguard.summer.core.Internal;
                    && !paramType.equals("com.github.dropguard.summer.web.HttpContext")) {
mport com.github.dropguard.summer.core.Internal;
                route.params.add(
mport com.github.dropguard.summer.core.Internal;
                        new RouteInfo.ParamInfo(
mport com.github.dropguard.summer.core.Internal;
                                paramName, "", paramType, RouteInfo.ParamBinding.BODY, hasValid));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String extractBindingName(
mport com.github.dropguard.summer.core.Internal;
            org.jboss.jandex.MethodParameterInfo param, DotName annotation, String defaultName) {
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance ann = param.annotation(annotation);
mport com.github.dropguard.summer.core.Internal;
        return (ann != null && ann.value() != null) ? ann.value().asString() : defaultName;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private String combinePaths(String base, String method) {
mport com.github.dropguard.summer.core.Internal;
        if (base.isEmpty()) return method;
mport com.github.dropguard.summer.core.Internal;
        if (method.isEmpty()) return base;
mport com.github.dropguard.summer.core.Internal;
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
mport com.github.dropguard.summer.core.Internal;
        String normalizedMethod = method.startsWith("/") ? method : "/" + method;
mport com.github.dropguard.summer.core.Internal;
        return normalizedBase + normalizedMethod;
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── AOP Bindings ──────────────────────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void detectAopBindings(List<BeanDefinition> beans) {
mport com.github.dropguard.summer.core.Internal;
        // Step 1: Collect all binding annotations (@InterceptorBinding-annotated)
mport com.github.dropguard.summer.core.Internal;
        Set<DotName> bindingAnnotations = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
        for (ClassInfo ci : index.getKnownClasses()) {
mport com.github.dropguard.summer.core.Internal;
            if (ci.isAnnotation() && ci.hasAnnotation(INTERCEPTOR_BINDING_DOT)) {
mport com.github.dropguard.summer.core.Internal;
                bindingAnnotations.add(ci.name());
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // Step 2: Identify interceptor beans and their binding annotations
mport com.github.dropguard.summer.core.Internal;
        Map<BeanDefinition, Set<DotName>> interceptorBindings = new HashMap<>();
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean instanceof ConfigPropertiesBean) continue;
mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
mport com.github.dropguard.summer.core.Internal;
            if (ci == null) continue;
mport com.github.dropguard.summer.core.Internal;
            if (ci.annotation(INTERCEPTOR_DOT) == null) continue;
mport com.github.dropguard.summer.core.Internal;
            Set<DotName> bindings = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
            for (AnnotationInstance ann : ci.declaredAnnotations()) {
mport com.github.dropguard.summer.core.Internal;
                if (bindingAnnotations.contains(ann.name())) {
mport com.github.dropguard.summer.core.Internal;
                    bindings.add(ann.name());
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (!bindings.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                interceptorBindings.put(bean, bindings);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
        // Step 3: Populate interceptorBindingAnnotations and match interceptors
mport com.github.dropguard.summer.core.Internal;
        for (BeanDefinition bean : beans) {
mport com.github.dropguard.summer.core.Internal;
            if (bean instanceof ConfigPropertiesBean) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
mport com.github.dropguard.summer.core.Internal;
            if (ci == null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            if (ci.annotation(INTERCEPTOR_DOT) != null) continue;
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            Set<String> bindings = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
            Set<DotName> targetBindings = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
            for (AnnotationInstance ann : ci.declaredAnnotations()) {
mport com.github.dropguard.summer.core.Internal;
                if (bindingAnnotations.contains(ann.name())) {
mport com.github.dropguard.summer.core.Internal;
                    String name = ann.name().toString();
mport com.github.dropguard.summer.core.Internal;
                    bindings.add(name);
mport com.github.dropguard.summer.core.Internal;
                    targetBindings.add(ann.name());
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            Map<String, Set<String>> methodBindings = new java.util.HashMap<>();
mport com.github.dropguard.summer.core.Internal;
            if (targetBindings.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                for (MethodInfo method : ci.methods()) {
mport com.github.dropguard.summer.core.Internal;
                    Set<String> methodAnnNames = new HashSet<>();
mport com.github.dropguard.summer.core.Internal;
                    for (AnnotationInstance ann : method.annotations()) {
mport com.github.dropguard.summer.core.Internal;
                        if (bindingAnnotations.contains(ann.name())) {
mport com.github.dropguard.summer.core.Internal;
                            String name = ann.name().toString();
mport com.github.dropguard.summer.core.Internal;
                            methodAnnNames.add(name);
mport com.github.dropguard.summer.core.Internal;
                            bindings.add(name);
mport com.github.dropguard.summer.core.Internal;
                        }
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                    if (!methodAnnNames.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                        methodBindings.put(method.name(), methodAnnNames);
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            // Binding annotations can also be declared on the bean's implemented
mport com.github.dropguard.summer.core.Internal;
            // interfaces — both type-level and method-level. A proxied service usually
mport com.github.dropguard.summer.core.Internal;
            // implements an interface (e.g. IssueService) that carries the binding
mport com.github.dropguard.summer.core.Internal;
            // annotation on its methods; the implementing class inherits it but Jandex
mport com.github.dropguard.summer.core.Internal;
            // ClassInfo.methods()/declaredAnnotations() do not include inherited
mport com.github.dropguard.summer.core.Internal;
            // interface members. Without this walk the interceptor is never applied.
mport com.github.dropguard.summer.core.Internal;
            for (String ifaceName : bean.interfaceNames) {
mport com.github.dropguard.summer.core.Internal;
                ClassInfo ifaceCi = index.getClassByName(DotName.createSimple(ifaceName));
mport com.github.dropguard.summer.core.Internal;
                if (ifaceCi == null) {
mport com.github.dropguard.summer.core.Internal;
                    continue;
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                for (AnnotationInstance ann : ifaceCi.declaredAnnotations()) {
mport com.github.dropguard.summer.core.Internal;
                    if (bindingAnnotations.contains(ann.name())) {
mport com.github.dropguard.summer.core.Internal;
                        String name = ann.name().toString();
mport com.github.dropguard.summer.core.Internal;
                        bindings.add(name);
mport com.github.dropguard.summer.core.Internal;
                        targetBindings.add(ann.name());
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
                for (MethodInfo method : ifaceCi.methods()) {
mport com.github.dropguard.summer.core.Internal;
                    for (AnnotationInstance ann : method.annotations()) {
mport com.github.dropguard.summer.core.Internal;
                        if (bindingAnnotations.contains(ann.name())) {
mport com.github.dropguard.summer.core.Internal;
                            String name = ann.name().toString();
mport com.github.dropguard.summer.core.Internal;
                            bindings.add(name);
mport com.github.dropguard.summer.core.Internal;
                            methodBindings
mport com.github.dropguard.summer.core.Internal;
                                    .computeIfAbsent(method.name(), k -> new HashSet<>())
mport com.github.dropguard.summer.core.Internal;
                                    .add(name);
mport com.github.dropguard.summer.core.Internal;
                        }
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            bean.interceptorBindingAnnotations =
mport com.github.dropguard.summer.core.Internal;
                    bindings.isEmpty() ? Set.of() : Set.copyOf(bindings);
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            // A class-level binding (@Logged on the bean class) intercepts every
mport com.github.dropguard.summer.core.Internal;
            // method. AotProxyGenerator keys that as "" (empty method name), so we
mport com.github.dropguard.summer.core.Internal;
            // must record it there — BeanEnrichment otherwise only populates
mport com.github.dropguard.summer.core.Internal;
            // methodBindingAnnotations with method-level entries (keyed by method
mport com.github.dropguard.summer.core.Internal;
            // name). RUNTIME's ProxyFactory derives class-level coverage directly
mport com.github.dropguard.summer.core.Internal;
            // from the implementation class annotations, so this key is the AOT
mport com.github.dropguard.summer.core.Internal;
            // engine's signal that the whole bean is bound.
mport com.github.dropguard.summer.core.Internal;
            Map<String, Set<String>> finalMethodBindings = new java.util.HashMap<>(methodBindings);
mport com.github.dropguard.summer.core.Internal;
            if (!targetBindings.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                Set<String> classLevel =
mport com.github.dropguard.summer.core.Internal;
                        targetBindings.stream()
mport com.github.dropguard.summer.core.Internal;
                                .map(DotName::toString)
mport com.github.dropguard.summer.core.Internal;
                                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
mport com.github.dropguard.summer.core.Internal;
                finalMethodBindings.put("", classLevel);
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
            if (!finalMethodBindings.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                bean.methodBindingAnnotations = finalMethodBindings;
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
            if (!bindings.isEmpty()) {
mport com.github.dropguard.summer.core.Internal;
                for (var entry : interceptorBindings.entrySet()) {
mport com.github.dropguard.summer.core.Internal;
                    for (DotName binding : entry.getValue()) {
mport com.github.dropguard.summer.core.Internal;
                        if (targetBindings.contains(binding)
mport com.github.dropguard.summer.core.Internal;
                                || methodBindings.values().stream()
mport com.github.dropguard.summer.core.Internal;
                                        .anyMatch(ms -> ms.contains(binding.toString()))) {
mport com.github.dropguard.summer.core.Internal;
                            bean.interceptors.add(entry.getKey());
mport com.github.dropguard.summer.core.Internal;
                            break;
mport com.github.dropguard.summer.core.Internal;
                        }
mport com.github.dropguard.summer.core.Internal;
                    }
mport com.github.dropguard.summer.core.Internal;
                }
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── @ExceptionHandler collection ───────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void collectExceptionHandlers(BeanDefinition bean, ClassInfo ci) {
mport com.github.dropguard.summer.core.Internal;
        for (MethodInfo method : ci.methods()) {
mport com.github.dropguard.summer.core.Internal;
            AnnotationInstance ann = method.annotation(EXCEPTION_HANDLER_DOT);
mport com.github.dropguard.summer.core.Internal;
            if (ann != null) {
mport com.github.dropguard.summer.core.Internal;
                String exClass = ann.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
                bean.exceptionHandlerMethods.add(
mport com.github.dropguard.summer.core.Internal;
                        new BeanDefinition.ExceptionHandlerEntry(
mport com.github.dropguard.summer.core.Internal;
                                method.name(), exClass, method.parametersCount()));
mport com.github.dropguard.summer.core.Internal;
            }
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    // ── @ConditionalOnBean collection ──────────────────────────────────
mport com.github.dropguard.summer.core.Internal;

mport com.github.dropguard.summer.core.Internal;
    private void collectConditions(BeanDefinition bean, ClassInfo ci) {
mport com.github.dropguard.summer.core.Internal;
        AnnotationInstance ann = ci.annotation(CONDITIONAL_ON_BEAN_DOT);
mport com.github.dropguard.summer.core.Internal;
        if (ann != null) {
mport com.github.dropguard.summer.core.Internal;
            bean.conditionalOnBeanType = ann.value().asClass().name().toString();
mport com.github.dropguard.summer.core.Internal;
        }
mport com.github.dropguard.summer.core.Internal;
    }
mport com.github.dropguard.summer.core.Internal;
}
