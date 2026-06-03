package summer.tck.aop.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.annotation.Annotation;
import org.junit.jupiter.api.Test;
import summer.aop.MethodMetadata;

/**
 * Abstract TCK for {@link MethodMetadata} implementations.
 *
 * <p>
 * Defines the behavioral contract that both Runtime and AOT engines must
 * satisfy. Each engine provides its own way to obtain a {@code MethodMetadata}
 * instance for a given method; the tests verify the metadata is correct.
 * </p>
 */
public abstract class AbstractMethodMetadataTCK {

	protected abstract MethodMetadata getMetadataFor(String methodName);

	@Test
	void getNameReturnsMethodName() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		assertEquals("taggedMethod", metadata.getName());
	}

	@Test
	void getNameReturnsPlainMethodName() {
		MethodMetadata metadata = getMetadataFor("plainMethod");
		assertEquals("plainMethod", metadata.getName());
	}

	@Test
	void getDeclaringClassReturnsInterface() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		assertEquals(MetadataSampleService.class, metadata.getDeclaringClass());
	}

	@Test
	void isAnnotationPresentReturnsTrueForTaggedMethod() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		assertTrue(metadata.isAnnotationPresent(MetadataTagged.class));
	}

	@Test
	void isAnnotationPresentReturnsFalseForPlainMethod() {
		MethodMetadata metadata = getMetadataFor("plainMethod");
		assertFalse(metadata.isAnnotationPresent(MetadataTagged.class));
	}

	@Test
	void getAnnotationReturnsInstanceForTaggedMethod() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		MetadataTagged annotation = metadata.getAnnotation(MetadataTagged.class);
		assertNotNull(annotation);
	}

	@Test
	void getAnnotationReturnsNullForPlainMethod() {
		MethodMetadata metadata = getMetadataFor("plainMethod");
		assertNull(metadata.getAnnotation(MetadataTagged.class));
	}

	@Test
	void isAnnotationPresentReturnsFalseForUnrelatedAnnotation() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		assertFalse(metadata.isAnnotationPresent(Deprecated.class));
	}

	@Test
	void getAnnotationReturnsNullForUnrelatedAnnotation() {
		MethodMetadata metadata = getMetadataFor("taggedMethod");
		assertNull(metadata.getAnnotation(Deprecated.class));
	}

	@Test
	void metadataForMethodWithArgsWorks() {
		MethodMetadata metadata = getMetadataFor("taggedWithArg");
		assertEquals("taggedWithArg", metadata.getName());
		assertEquals(MetadataSampleService.class, metadata.getDeclaringClass());
		assertTrue(metadata.isAnnotationPresent(MetadataTagged.class));
	}
}
