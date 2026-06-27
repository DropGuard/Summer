package summer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Immutable bean container that serves as the DI engine's output.
 *
 * <p>
 * Use {@link Builder} during the construction phase to register beans, then call
 * {@link Builder#build()} to produce an immutable container. The builder is
 * discarded after that — no further mutation is possible.
 * </p>
 */
public final class BeanContainer implements AutoCloseable {

	private final Map<Class<?>, Object> singletons;
	private final Engine engine;

	private BeanContainer(Map<Class<?>, Object> singletons, Engine engine) {
		this.singletons = Map.copyOf(singletons);
		this.engine = engine;
	}

	/** Returns which engine produced this container. */
	public Engine engine() {
		return engine;
	}

	/** Returns all type keys registered in the container (interfaces included). */
	public Set<Class<?>> componentTypes() {
		return singletons.keySet();
	}

	// ---- Read-only bean lookup ----

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
		List<Object> reversed = new ArrayList<>(singletons.values());
		Collections.reverse(reversed);
		for (Object bean : reversed) {
			if (bean instanceof AutoCloseable) {
				try {
					((AutoCloseable) bean).close();
				} catch (Exception e) {
					System.err.println("[Summer] Error closing: " + e);
				}
			}
		}
	}

	// ---- Builder ----

	/**
	 * Mutable builder for {@link BeanContainer}. Used during the context
	 * construction phase to register singletons, interface bindings, and to peek
	 * at already-registered beans.
	 *
	 * <p>
	 * Once {@link #build()} is called, the builder is consumed and the resulting
	 * container is immutable. The builder reference should be discarded.
	 * </p>
	 */
	public static final class Builder {

		private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();
		private boolean consumed;

		/**
		 * Registers a bean instance under the given type key (exact match).
		 */
		public void registerSingleton(Class<?> type, Object instance) {
			checkMutable();
			singletons.put(type, instance);
		}

		/**
		 * Registers a bean under an interface type, retaining the same instance. If the
		 * interface key is already occupied, does nothing (first-wins). Multiple
		 * implementations of the same interface are allowed for {@code List<T>}
		 * injection; the first to register holds the key for single-bean lookup.
		 */
		public void registerInterface(Class<?> iface, Object instance) {
			checkMutable();
			singletons.putIfAbsent(iface, instance);
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
			checkMutable();
			return singletons.remove(type);
		}

		/**
		 * Removes all entries whose value is {@code ==} to the given instance (identity
		 * check). Returns the instance that was removed, or null.
		 */
		public Object removeByInstance(Object instance) {
			checkMutable();
			singletons.values().removeIf(v -> v == instance);
			return instance;
		}

		/**
		 * Consumes this builder and produces an immutable {@link BeanContainer}.
		 * After this call, the builder cannot be used for further registration.
		 */
		public BeanContainer build(Engine engine) {
			checkMutable();
			consumed = true;
			return new BeanContainer(singletons, engine);
		}

		/**
		 * Convenience overload — defaults to {@link Engine#RUNTIME}.
		 */
		public BeanContainer build() {
			return build(Engine.RUNTIME);
		}

		private void checkMutable() {
			if (consumed) {
				throw new IllegalStateException(
						"Builder already consumed — BeanContainer has been built");
			}
		}
	}
}
