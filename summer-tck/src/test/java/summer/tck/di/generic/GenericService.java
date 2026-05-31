package summer.tck.di.generic;

/**
 * Generic service interface for testing generic interface dependency resolution.
 */
public interface GenericService<T> {
	T process(T input);
}
