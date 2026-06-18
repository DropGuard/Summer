package summer.aot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean discovered via {@code @Component} (or meta-annotation like
 * {@code @RestController}).
 */
public final class ComponentBean extends BeanDefinition {

	public final List<String> constructorParamTypes = new ArrayList<>();
	public final Map<Integer, String> listElementTypes = new HashMap<>();
	public final List<String> interfaceNames = new ArrayList<>();
	boolean needsProxy;
	final List<BeanDefinition> interceptors = new ArrayList<>();
	public boolean isAutoCloseable;

	public ComponentBean(String qualifiedName, String simpleName) {
		super(qualifiedName, simpleName);
	}
}
