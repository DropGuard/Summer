package summer.aot;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean produced by a {@code @Bean} method in a {@code @Configuration} class.
 */
public final class FactoryBean extends BeanDefinition {

	public String configClassName;
	public String producerMethodName;
	public final List<String> producerParamTypes = new ArrayList<>();

	public FactoryBean(String qualifiedName, String simpleName) {
		super(qualifiedName, simpleName);
	}
}
