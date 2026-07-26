package com.github.dropguard.summer.tck;

/**
 * TCK root base class - provides common lifecycle utilities.
 *
 * <p>
 * All TCK tests inherit from this class to get:
 * <ul>
 * <li>Safe resource cleanup utilities</li>
 * <li>Consistent error handling patterns</li>
 * </ul>
 */
public abstract class AbstractTCK {

	/**
	 * Safely close a resource, ignoring null.
	 */
	protected static void closeQuietly(AutoCloseable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (Exception e) {
				throw new RuntimeException("Failed to close resource", e);
			}
		}
	}
}
