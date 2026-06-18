package summer.tck.web;

import summer.core.BeanContainer;

/**
 * AOT engine middleware TCK.
 */
public class AotMiddlewareTest extends AbstractMiddlewareTCK {

	@Override
	protected BeanContainer createContext() {
		try {
			Class<?> aotClass = Class.forName("summer.core.aot.GeneratedAotContext");
			return (BeanContainer) aotClass.getMethod("create").invoke(null);
		} catch (Exception e) {
			throw new RuntimeException("AOT context not available", e);
		}
	}
}
