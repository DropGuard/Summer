package summer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

/**
 * Mutable bean registry used during the context building phase.
 *
 * <p>
 * Supports incremental registration, peek-based dependency checking, and
 * removal for condition evaluation. Once the context is fully built, the
 * registry is frozen into an immutable {@link BeanContainer}.
 * </p>
 */
public final class BeanRegistry {

	private final Map<Class<?>, Object> singletons = new LinkedHashMap<>();

	/**
	 * Registers a bean instance under the given type key (exact match).
	 */
	public void registerSingleton(Class<?> type, Object instance) {
		singletons.put(type, instance);
	}

	/**
	 * Registers a bean under an interface type, retaining the same instance. If the
	 * interface key is already occupied, does nothing (first-wins).
	 */
	public void registerInterface(Class<?> iface, Object instance) {
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
		Object bean = singletons.get(type);
		if (bean != null) {
			return (T) bean;
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

	/**
	 * Returns the internal singleton map (read-only view). Used by generated AOT
	 * code for validation-phase iteration.
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
	 * check). Returns the instance that was removed, or null.
	 */
	public Object removeByInstance(Object instance) {
		singletons.values().removeIf(v -> v == instance);
		return instance;
	}
}