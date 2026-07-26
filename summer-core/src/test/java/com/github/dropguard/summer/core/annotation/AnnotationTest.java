package com.github.dropguard.summer.core.annotation;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.Component;
import org.junit.jupiter.api.Test;

/**
 * Tests for Summer annotations.
 */
class AnnotationTest {

	@Test
	void shouldHaveComponentAnnotation() {
		Component annotation = TestComponent.class.getAnnotation(Component.class);
		assertNotNull(annotation);
		assertEquals("", annotation.value());
	}

	@Test
	void shouldHaveComponentAnnotationWithValue() {
		Component annotation = TestComponentWithValue.class.getAnnotation(Component.class);
		assertNotNull(annotation);
		assertEquals("test", annotation.value());
	}

	@Test
	void shouldHaveBeanAnnotation() {
		Bean annotation = getBeanAnnotation();
		assertNotNull(annotation);
	}

	@Test
	void shouldHaveConfigurationAnnotation() {
		Configuration annotation = TestConfiguration.class.getAnnotation(Configuration.class);
		assertNotNull(annotation);
	}

	@Test
	void shouldHaveConditionalOnBeanAnnotation() {
		ConditionalOnBean annotation = TestConditionalComponent.class.getAnnotation(ConditionalOnBean.class);
		assertNotNull(annotation);
		assertEquals(String.class, annotation.value());
	}

	@Test
	void shouldHaveReplacesAnnotation() {
		Replaces annotation = TestReplacement.class.getAnnotation(Replaces.class);
		assertNotNull(annotation);
		assertEquals(TestComponent.class, annotation.value());
	}

	@Test
	void shouldSupportComponentAnnotationOnClass() {
		assertTrue(TestComponent.class.isAnnotationPresent(Component.class));
	}

	@Test
	void shouldSupportConfigurationAnnotationOnClass() {
		assertTrue(TestConfiguration.class.isAnnotationPresent(Configuration.class));
	}

	@Test
	void shouldSupportConditionalOnBeanAnnotationOnClass() {
		assertTrue(TestConditionalComponent.class.isAnnotationPresent(ConditionalOnBean.class));
	}

	@Test
	void shouldSupportReplacesAnnotationOnClass() {
		assertTrue(TestReplacement.class.isAnnotationPresent(Replaces.class));
	}

	// Helper method to get Bean annotation
	private Bean getBeanAnnotation() {
		try {
			return TestConfiguration.class.getDeclaredMethod("testBean").getAnnotation(Bean.class);
		} catch (NoSuchMethodException e) {
			fail("TestBean method not found");
			return null;
		}
	}

	// Test helper classes
	@Component
	public static class TestComponent {
	}

	@Component("test")
	public static class TestComponentWithValue {
	}

	@Configuration
	public static class TestConfiguration {
		@Bean
		public String testBean() {
			return "test";
		}
	}

	@Component
	@ConditionalOnBean(String.class)
	public static class TestConditionalComponent {
	}

	@Component
	@Replaces(TestComponent.class)
	public static class TestReplacement {
	}
}
