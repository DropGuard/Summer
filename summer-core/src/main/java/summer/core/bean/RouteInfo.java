package summer.core.bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Route metadata shared by both DI engines.
 *
 * <p>
 * The fields here are the cross-engine contract: string identifiers
 * ({@link #controllerClass}, {@link #methodName}, {@link #returnType}) plus the
 * parameter binding list. Both the Runtime and AOT engines read exactly these
 * strings — the AOT engine emits static handler calls from them, the Runtime
 * engine resolves the reflective {@code Method} from them at registration time
 * (inside {@code summer-runtime}, which is the only module permitted to hold a
 * {@code java.lang.reflect.Method}).
 * </p>
 *
 * <p>
 * This type deliberately holds no {@code java.lang.reflect.Method}: reflection
 * types belong to the runtime engine, never to the dual-engine shared layer in
 * {@code summer-core}.
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
		/** Parameter name (for diagnostics), not the binding key. */
		public final String name;
		/** The binding key: {@code @PathParam}/{@code @QueryParam} value, or empty. */
		public final String bindingName;
		public final String type;
		public final ParamBinding binding;
		public final boolean validated;

		public ParamInfo(String name, String type, ParamBinding binding) {
			this(name, "", type, binding, false);
		}

		public ParamInfo(String name, String type, ParamBinding binding, boolean validated) {
			this(name, "", type, binding, validated);
		}

		/**
		 * @param bindingName
		 *            the {@code @PathParam}/{@code @QueryParam} value (empty if none)
		 */
		public ParamInfo(String name, String bindingName, String type, ParamBinding binding, boolean validated) {
			this.name = name;
			this.bindingName = bindingName;
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
	 * Runtime-only convenience: the controller class object, used by the runtime
	 * engine to resolve the handler {@code Method} without re-scanning. Always
	 * {@code null} on the AOT path, which reads the string fields for code
	 * generation. Holds a {@code Class} token (not a {@code Method}) so it stays
	 * clear of {@code java.lang.reflect} types in the shared layer.
	 */
	public final Class<?> controllerType;

	public RouteInfo(String httpMethod, String path, String controllerClass, String methodName, String returnType) {
		this(httpMethod, path, controllerClass, methodName, returnType, null);
	}

	public RouteInfo(String httpMethod, String path, String controllerClass, String methodName, String returnType,
			Class<?> controllerType) {
		this.httpMethod = httpMethod;
		this.path = path;
		this.controllerClass = controllerClass;
		this.methodName = methodName;
		this.returnType = returnType;
		this.controllerType = controllerType;
	}

	@Override
	public String toString() {
		return httpMethod + " " + path + " -> " + controllerClass + "." + methodName;
	}
}
