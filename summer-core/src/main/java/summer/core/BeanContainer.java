package summer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summer.core.bean.RouteInfo;
import summer.core.config.ShutdownConfig;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Immutable bean container that serves as the DI engine's output.
 *
 * <p>
 * Use {@link Builder} during the construction phase to register beans, then
 * call {@link Builder#build()} to produce an immutable container. The builder
 * is discarded after that — no further mutation is possible.
 * </p>
 */
public final class BeanContainer implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(BeanContainer.class);

	private final Map<Class<?>, Object> singletons;
	private final Engine engine;
	private final List<RouteInfo> routes;
	private final ShutdownContext shutdownContext = ShutdownContext.create();

	private BeanContainer(Map<Class<?>, Object> singletons, Engine engine, List<RouteInfo> routes) {
		// MUST preserve insertion order for correct shutdown (reverse order of
		// creation)
		this.singletons = Collections.unmodifiableMap(new LinkedHashMap<>(singletons));
		this.engine = engine;
		this.routes = routes;
	}

	/** Returns which engine produced this container. */
	public Engine engine() {
		return engine;
	}

	/** Returns all type keys registered in the container (interfaces included). */
	public Set<Class<?>> componentTypes() {
		return singletons.keySet();
	}

	/** Returns route metadata collected during container construction. */
	public List<RouteInfo> routes() {
		return routes;
	}

	// ---- Read-only bean lookup ----

	/**
	 * Gets a bean by type. First tries exact key match, then falls back to
	 * {@code isInstance} scanning. Throws if zero or multiple matches.
	 */
	@SuppressWarnings("unchecked")
	public <T> T getBean(Class<T> type) {
		if (type == null) {
			throw new IllegalArgumentException("getBean requires a non-null type");
		}
		Object exact = singletons.get(type);
		if (exact != null) {
			return (T) exact;
		}
		List<T> matches = getBeans(type);
		if (matches.isEmpty()) {
			throw new NoSuchBeanException("No bean found of type: " + type.getName());
		}
		if (matches.size() > 1) {
			throw new AmbiguousBeanException("Multiple beans found for type: " + type.getName());
		}
		return matches.get(0);
	}

	/**
	 * Returns all beans whose type is assignable to the given type.
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getBeans(Class<T> type) {
		if (type == null) {
			throw new IllegalArgumentException("getBeans requires a non-null type");
		}
		List<T> result = new ArrayList<>();
		for (Object bean : singletons.values()) {
			if (type.isInstance(bean) && !result.contains(bean)) {
				result.add((T) bean);
			}
		}
		return result;
	}

	/**
	 * Checks whether a bean of the given type is registered (exact key or
	 * assignable).
	 */
	public boolean containsBean(Class<?> type) {
		if (singletons.containsKey(type)) {
			return true;
		}
		for (Object bean : singletons.values()) {
			if (type.isInstance(bean)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void close() throws Exception {
		// Phase 1: input drivers (servers) tear down first, each via its own
		// registered task (stop accepting, drain in-flight, release resources),
		// in reverse registration order — so external traffic stops before
		// anything underneath is torn down.
		shutdownContext.runAll();

		// Phase 2: reverse-creation-order destruction of the remaining
		// AutoCloseable beans (data sources, pools, ...) now that no traffic flows.
		List<Object> reversed = new ArrayList<>(singletons.values());
		Collections.reverse(reversed);
		for (Object bean : reversed) {
			if (bean instanceof AutoCloseable ac) {
				try {
					ac.close();
				} catch (Exception e) {
					log.error("[Summer] Error closing bean {}: {}", bean.getClass().getName(), e);
				}
			}
		}
	}

	/**
	 * Registers a shutdown task with the container's {@link ShutdownContext}. Tasks
	 * run in reverse registration order on {@link #close()}, before the remaining
	 * {@link AutoCloseable} beans are closed. Input drivers (servers) call this
	 * from their startup callback to encapsulate their own teardown staging.
	 */
	public void addShutdownTask(Runnable task) {
		shutdownContext.addShutdownTask(task);
	}

	/**
	 * Resolves the global {@link ShutdownConfig}, falling back to defaults when no
	 * {@code @ConfigurationProperties} bean is present. Used by input drivers at
	 * registration time to read the in-flight drain timeout.
	 */
	public ShutdownConfig getShutdownConfig() {
		Object bean = singletons.get(ShutdownConfig.class);
		return bean instanceof ShutdownConfig c ? c : new ShutdownConfig(10000L);
	}

	// ---- Builder ----

	/**
	 * Mutable builder for {@link BeanContainer}. Used during the context
	 * construction phase to register beans and peek at already-registered beans.
	 */
	public static final class Builder {

		private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();
		private List<RouteInfo> routes = List.of();

		/**
		 * Registers a bean instance under the given type key.
		 */
		public void register(Class<?> type, Object instance) {
			singletons.put(type, instance);
		}

		/**
		 * Sets route metadata collected during container construction.
		 */
		public void routes(List<RouteInfo> routes) {
			this.routes = List.copyOf(routes);
		}

		/**
		 * Returns the instance registered under the exact type key, or {@code null} if
		 * not present. Does not perform assignability matching.
		 */
		public Object peek(Class<?> type) {
			return singletons.get(type);
		}

		/**
		 * Gets a bean by type. First tries exact key match, then falls back to
		 * {@code isInstance} scanning. Throws if zero or multiple matches.
		 */
		@SuppressWarnings("unchecked")
		public <T> T getBean(Class<T> type) {
			Object exact = singletons.get(type);
			if (exact != null) {
				return (T) exact;
			}
			List<T> matches = getBeans(type);
			if (matches.isEmpty()) {
				throw new NoSuchBeanException("No bean found of type: " + type.getName());
			}
			if (matches.size() > 1) {
				throw new AmbiguousBeanException("Multiple beans found for type: " + type.getName());
			}
			return matches.get(0);
		}

		/**
		 * Returns all beans whose type is assignable to the given type.
		 */
		@SuppressWarnings("unchecked")
		public <T> List<T> getBeans(Class<T> type) {
			List<T> result = new ArrayList<>();
			for (Object bean : singletons.values()) {
				if (type.isInstance(bean) && !result.contains(bean)) {
					result.add((T) bean);
				}
			}
			return result;
		}

		/**
		 * Returns a read-only view of all registered singletons.
		 */
		public Map<Class<?>, Object> singletons() {
			return Collections.unmodifiableMap(singletons);
		}

		/**
		 * Removes the bean registered under the exact type key. Returns the removed
		 * instance, or null.
		 */
		public Object remove(Class<?> type) {
			return singletons.remove(type);
		}

		/**
		 * Removes all entries whose value is {@code ==} to the given instance (identity
		 * check). Returns the instance that was removed.
		 */
		public Object removeByInstance(Object instance) {
			singletons.values().removeIf(v -> v == instance);
			return instance;
		}

		/**
		 * Produces an immutable {@link BeanContainer}.
		 */
		public BeanContainer build(Engine engine) {
			return new BeanContainer(singletons, engine, routes);
		}

		/**
		 * Convenience overload — defaults to {@link Engine#RUNTIME}.
		 */
		public BeanContainer build() {
			return build(Engine.RUNTIME);
		}
	}
}
