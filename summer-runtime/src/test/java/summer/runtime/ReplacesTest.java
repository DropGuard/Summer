package summer.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.annotation.Bean;
import summer.core.annotation.Configuration;
import summer.core.annotation.Replaces;
import summer.core.exception.DuplicateReplacementException;

public class ReplacesTest {

	// --- Fixture classes ---

	public interface Service {
		String name();
	}

	@Configuration
	public static class OriginalConfig {
		@Bean
		public Service service() {
			return () -> "original";
		}
	}

	@Configuration
	@Replaces(OriginalConfig.class)
	public static class ReplacementConfig {
		@Bean
		public Service service() {
			return () -> "replacement";
		}
	}

	// --- Tests ---

	@Test
	void replacesExcludesOriginalConfig() {
		RuntimeApplicationContext context = new RuntimeApplicationContext();
		context.registerComponent(OriginalConfig.class);
		context.registerComponent(ReplacementConfig.class);
		context.initializeBeans();

		Service service = context.getBean(Service.class);
		assertEquals("replacement", service.name());
		assertFalse(context.getRegisteredTypes().contains(OriginalConfig.class), "OriginalConfig should be excluded");
	}

	@Test
	void duplicateReplacesThrows() {
		@Configuration
		@Replaces(OriginalConfig.class)
		class AnotherReplacement {
			@Bean
			public Service service() {
				return () -> "another";
			}
		}

		RuntimeApplicationContext context = new RuntimeApplicationContext();
		context.registerComponent(OriginalConfig.class);
		context.registerComponent(ReplacementConfig.class);
		context.registerComponent(AnotherReplacement.class);

		assertThrows(DuplicateReplacementException.class, context::initializeBeans);
	}
}
