package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.RecordComponent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import summer.core.BeanContainer;
import summer.core.config.DefaultValue;
import summer.grpc.config.GrpcTlsConfig;
import summer.test.TestContainerBuilder;
import summer.web.middleware.CorsConfig;

/**
 * Integration tests for cross-module {@code @ConfigurationProperties} binding.
 *
 * <p>
 * Catches issues where {@code @DefaultValue} annotations on record components
 * are not visible at runtime for records in other modules — a scenario the
 * unit tests in {@code summer-runtime} cannot cover.
 * </p>
 */
class CrossModuleConfigBindingTest {

	private BeanContainer context;

	@AfterEach
	void tearDown() {
		if (context instanceof AutoCloseable ac) {
			try { ac.close(); } catch (Exception ignored) {}
		}
	}

	private BeanContainer createContext(Class<?>... components) {
		context = TestContainerBuilder.buildRuntime(components);
		return context;
	}

	// ── Annotation visibility ────────────────────────────────────────

	@Nested
	class AnnotationVisibility {

		@Test
		void defaultValueVisibleOnCorsConfig() {
			assertAllComponentsAnnotated(CorsConfig.class);
		}

		@Test
		void defaultValueVisibleOnGrpcTlsConfigEnabled() {
			// Only 'enabled' has @DefaultValue; cert fields are nullable (no default)
			RecordComponent enabled = GrpcTlsConfig.class.getRecordComponents()[0];
			DefaultValue ann = enabled.getAnnotation(DefaultValue.class);
			assertNotNull(ann, "@DefaultValue not visible on GrpcTlsConfig.enabled");
			assertEquals("false", ann.value());
		}

		private void assertAllComponentsAnnotated(Class<?> recordClass) {
			for (RecordComponent rc : recordClass.getRecordComponents()) {
				DefaultValue ann = rc.getAnnotation(DefaultValue.class);
				assertNotNull(ann,
						"@DefaultValue not visible on " + recordClass.getSimpleName() + "." + rc.getName()
								+ " — possible cross-module annotation visibility bug");
			}
		}
	}

	// ── Binding behavior ─────────────────────────────────────────────

	@Nested
	class BindingBehavior {

		@Test
		void corsConfigBindsWithDefaults() {
			BeanContainer ctx = createContext(CorsConfig.class);
			CorsConfig config = ctx.getBean(CorsConfig.class);

			assertNotNull(config, "CorsConfig should bind with defaults");
			assertEquals("*", config.allowedOrigins());
			assertEquals("GET, POST, PUT, DELETE, OPTIONS", config.allowedMethods());
			assertEquals("Content-Type, Authorization", config.allowedHeaders());
			assertEquals(3600, config.maxAge());
		}

		@Test
		void grpcTlsConfigBindsWithNulls() {
			BeanContainer ctx = createContext(GrpcTlsConfig.class);
			GrpcTlsConfig config = ctx.getBean(GrpcTlsConfig.class);

			assertNotNull(config, "GrpcTlsConfig should bind");
			assertFalse(config.enabled(), "enabled should use @DefaultValue(false)");
			assertNull(config.certChain(), "certChain should be null when not in YAML");
			assertNull(config.privateKey(), "privateKey should be null when not in YAML");
			assertNull(config.trustCert(), "trustCert should be null when not in YAML");
		}
	}
}
