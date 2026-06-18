package summer.aot;

import java.util.ArrayList;
import java.util.List;

/**
 * Route metadata collected at compile time for AOT code generation.
 *
 * <p>
 * Contains all information needed to generate static route registration code
 * without runtime reflection.
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

	public RouteInfo(String httpMethod, String path, String controllerClass, String methodName, String returnType) {
		this.httpMethod = httpMethod;
		this.path = path;
		this.controllerClass = controllerClass;
		this.methodName = methodName;
		this.returnType = returnType;
	}

	@Override
	public String toString() {
		return httpMethod + " " + path + " -> " + controllerClass + "." + methodName;
	}
}
