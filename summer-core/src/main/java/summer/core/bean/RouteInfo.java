package summer.core.bean;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Route metadata shared by both DI engines.
 *
 * <p>
 * String fields ({@link #controllerClass}, {@link #methodName}) are the
 * cross-engine contract — populated on both Runtime and AOT paths.
 * </p>
 *
 * <p>
 * The {@link #controllerType} and {@link #handlerMethod} fields are
 * Runtime-only conveniences that eliminate re-reflection inside
 * {@code RuntimeRouteRegistrar}. They are always {@code null} on the AOT path,
 * which reads the string fields for code generation.
 * </p>
 */
public final class RouteInfo {

	public enum ParamBinding {
		PATH, QUERY, BODY, PAGEABLE
	}

	/**
	 * Method parameter binding metadata.
	 */
	public static final class ParamInfo {
		public final String name;
		public final String type;
		public final ParamBinding binding;
		public final boolean validated;

		public ParamInfo(String name, String type, ParamBinding binding) {
			this(name, type, binding, false);
		}

		public ParamInfo(String name, String type, ParamBinding binding, boolean validated) {
			this.name = name;
			this.type = type;
			this.binding = binding;
			this.validated = validated;
		}

		@Override
		public String toString() {
			return (validated ? "@Valid " : "") + binding + " " + type + " " + name;
		}
	}

	public final String httpMethod;
	public final String path;
	public final String controllerClass;
	public final String methodName;
	public final String returnType;
	public final List<ParamInfo> params = new ArrayList<>();

	/**
	 * Runtime-only: the controller class object. Always {@code null} on the AOT
	 * path.
	 */
	public final Class<?> controllerType;
	/**
	 * Runtime-only: the handler method object. Always {@code null} on the AOT path.
	 */
	public final Method handlerMethod;

	public RouteInfo(String httpMethod, String path, String controllerClass, String methodName, String returnType) {
		this(httpMethod, path, controllerClass, methodName, returnType, null, null);
	}

	public RouteInfo(String httpMethod, String path, String controllerClass, String methodName, String returnType,
			Class<?> controllerType, Method handlerMethod) {
		this.httpMethod = httpMethod;
		this.path = path;
		this.controllerClass = controllerClass;
		this.methodName = methodName;
		this.returnType = returnType;
		this.controllerType = controllerType;
		this.handlerMethod = handlerMethod;
	}

	@Override
	public String toString() {
		return httpMethod + " " + path + " -> " + controllerClass + "." + methodName;
	}
}
