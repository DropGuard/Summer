package summer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.ApplicationContext;
import summer.web.Handler;
import summer.web.HttpMethod;
import summer.web.HttpRouter;
import summer.web.PathUtils;
import summer.web.RouteRegistrar;
import summer.web.annotation.Delete;
import summer.web.annotation.Get;
import summer.web.annotation.Post;
import summer.web.annotation.Put;
import summer.web.annotation.RestController;

public class RuntimeRouteRegistrar implements RouteRegistrar {

	private static final Logger log = LoggerFactory.getLogger(RuntimeRouteRegistrar.class);

	private static final DotName REST_CONTROLLER = DotName.createSimple(RestController.class);
	private static final Map<DotName, HttpMethod> HTTP_METHODS_JANDEX = Map.of(DotName.createSimple(Get.class),
			HttpMethod.GET, DotName.createSimple(Post.class), HttpMethod.POST, DotName.createSimple(Put.class),
			HttpMethod.PUT, DotName.createSimple(Delete.class), HttpMethod.DELETE);

	private static final Map<Class<? extends Annotation>, HttpMethod> HTTP_METHODS_REFLECTION = Map.of(Get.class,
			HttpMethod.GET, Post.class, HttpMethod.POST, Put.class, HttpMethod.PUT, Delete.class, HttpMethod.DELETE);

	private final HttpParameterResolverChain resolverChain;
	private final IndexView index;

	public RuntimeRouteRegistrar(HttpParameterResolverChain resolverChain, IndexView index) {
		this.resolverChain = resolverChain;
		this.index = index;
	}

	@Override
	public void registerControllers(HttpRouter.Builder builder, ApplicationContext context) {
		Set<String> jandexDiscovered = new HashSet<>();

		if (index != null) {
			for (Map.Entry<DotName, HttpMethod> entry : HTTP_METHODS_JANDEX.entrySet()) {
				Collection<AnnotationInstance> annotations = index.getAnnotations(entry.getKey());
				for (AnnotationInstance ann : annotations) {
					MethodInfo method = ann.target().asMethod();
					ClassInfo controllerClass = method.declaringClass();
					if (!controllerClass.hasAnnotation(REST_CONTROLLER))
						continue;
					jandexDiscovered.add(controllerClass.name().toString());
					registerFromJandex(builder, context, ann, method, controllerClass, entry.getValue());
				}
			}
		}

		for (Class<?> clazz : context.getRegisteredTypes()) {
			if (!clazz.isAnnotationPresent(RestController.class))
				continue;
			if (jandexDiscovered.contains(clazz.getName()))
				continue;
			for (Method method : clazz.getMethods()) {
				registerFromReflection(builder, context, clazz, method);
			}
		}
	}

	private void registerFromJandex(HttpRouter.Builder builder, ApplicationContext context, AnnotationInstance ann,
			MethodInfo method, ClassInfo controllerClass, HttpMethod httpMethod) {
		String controllerPath = controllerClass.annotation(REST_CONTROLLER).value().asString();
		String methodPath = annotationValue(ann);
		String fullPath = PathUtils.combinePaths(controllerPath, methodPath);

		try {
			Class<?> clazz = Class.forName(controllerClass.name().toString());
			Object instance = context.getBean(clazz);
			Class<?>[] paramTypes = method.parameterTypes().stream().map(pt -> {
				try {
					return Class.forName(pt.name().toString());
				} catch (ClassNotFoundException e) {
					throw new RuntimeException(e);
				}
			}).toArray(Class<?>[]::new);
			java.lang.reflect.Method javaMethod = clazz.getMethod(method.name(), paramTypes);

			Handler handler = HandlerFactory.create(instance, javaMethod, resolverChain);
			switch (httpMethod) {
				case GET -> builder.get(fullPath, handler);
				case POST -> builder.post(fullPath, handler);
				case PUT -> builder.put(fullPath, handler);
				case DELETE -> builder.delete(fullPath, handler);
			}
		} catch (Exception e) {
			log.warn("[Summer] Failed to register route {} {}: {}", httpMethod, fullPath, e.getMessage());
		}
	}

	private void registerFromReflection(HttpRouter.Builder builder, ApplicationContext context, Class<?> clazz,
			Method method) {
		for (var entry : HTTP_METHODS_REFLECTION.entrySet()) {
			Annotation ann = method.getAnnotation(entry.getKey());
			if (ann != null) {
				String path = PathUtils.combinePaths(clazz.getAnnotation(RestController.class).value(),
						annotationValueReflection(ann));
				Handler handler = HandlerFactory.create(context.getBean(clazz), method, resolverChain);
				switch (entry.getValue()) {
					case GET -> builder.get(path, handler);
					case POST -> builder.post(path, handler);
					case PUT -> builder.put(path, handler);
					case DELETE -> builder.delete(path, handler);
				}
				return;
			}
		}
	}

	private static String annotationValue(AnnotationInstance ann) {
		AnnotationValue value = ann.value();
		return value != null ? value.asString() : "";
	}

	private static String annotationValueReflection(Annotation ann) {
		try {
			return (String) ann.annotationType().getMethod("value").invoke(ann);
		} catch (Exception e) {
			return "";
		}
	}
}
