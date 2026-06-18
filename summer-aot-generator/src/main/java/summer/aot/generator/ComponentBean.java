package summer.aot.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean discovered via {@code @Component} (or meta-annotation like
 * {@code @RestController}).
 */
public final class ComponentBean extends BeanDefinition {

	/**
	 * Pre-computed method signature for AOT proxy generation.
	 */
	public record ProxyMethodInfo(String methodName, String returnType, List<ParamInfo> params,
			List<String> exceptions, boolean shouldIntercept) {

		public record ParamInfo(String name, String type) {
		}
	}

	public final List<String> constructorParamTypes = new ArrayList<>();
	public final Map<Integer, String> listElementTypes = new HashMap<>();
	boolean needsProxy;
	boolean classLevelBinding;
	final List<BeanDefinition> interceptors = new ArrayList<>();
	public boolean isAutoCloseable;
	public final List<ProxyMethodInfo> proxyMethods = new ArrayList<>();

	public ComponentBean(String qualifiedName, String simpleName) {
		super(qualifiedName, simpleName);
	}
}
