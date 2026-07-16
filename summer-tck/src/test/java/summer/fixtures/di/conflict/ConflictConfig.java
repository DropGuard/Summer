package summer.fixtures.di.conflict;

import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;

/**
 * Test fixture for multi-module conflict detection.
 *
 * <p>
 * Intentionally creates ambiguous beans (two implementations of the same
 * interface). Scope to the test's own module so discovery intentional ambiguity
 * is resolved by the engine.
 * </p>
 */
@Configuration
public class ConflictConfig {

	@Bean
	public ConflictService conflictServiceImpl1() {
		return new ConflictServiceImpl1();
	}

	@Bean
	public ConflictService conflictServiceImpl2() {
		return new ConflictServiceImpl2();
	}

	@Bean
	public ConflictClient conflictClient(ConflictService conflictService) {
		return new ConflictClient(conflictService);
	}
}
