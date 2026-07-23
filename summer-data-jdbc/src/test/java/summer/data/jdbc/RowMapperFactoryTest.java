package summer.data.jdbc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import summer.data.jdbc.RowMapperFactory.FieldMeta;
import summer.data.jdbc.RowMapperFactory.RowModelMeta;

/**
 * Unit tests for {@link RowMapperFactory}'s type support contract — verifies
 * that supported field types are accepted and unsupported ones fail fast at
 * assembly, so a user mapping a {@code jsonb} / nested / collection column gets
 * a clear error rather than a runtime row-mapping surprise.
 */
class RowMapperFactoryTest {

	private static RowModelMeta metaWith(String... typeNames) {
		List<FieldMeta> fields = new java.util.ArrayList<>();
		int i = 0;
		for (String t : typeNames) {
			fields.add(new FieldMeta("f" + (i++), t));
		}
		return new RowModelMeta("com.example.Model", "com.example", "Model", "model", fields);
	}

	@Test
	void acceptsJdbcNativeTypes() {
		assertDoesNotThrow(() -> RowMapperFactory.assertSupported(metaWith("java.lang.Long", "java.lang.String",
				"java.math.BigDecimal", "java.util.UUID", "java.time.LocalDateTime")));
	}

	@Test
	void rejectsCollectionFieldAtAssembly() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> RowMapperFactory.assertSupported(metaWith("java.util.List<java.lang.String>")));
		assertTrue(ex.getMessage().contains("Unsupported @RowModel field type"));
	}

	@Test
	void rejectsNestedRecordFieldAtAssembly() {
		IllegalStateException ex = assertThrows(IllegalStateException.class,
				() -> RowMapperFactory.assertSupported(metaWith("com.example.Address")));
		assertTrue(ex.getMessage().contains("Unsupported @RowModel field type"));
	}
}
