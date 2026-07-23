package summer.data.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.RecordComponentInfo;

/**
 * Shared RowMapper metadata extraction and construction. Both the AOT engine
 * and the runtime engine use this to discover {@code @RowModel} records and
 * create appropriate {@code RowMapper} instances.
 */
public final class RowMapperFactory {

	private static final DotName ROW_MODEL_DOT = DotName.createSimple("summer.data.jdbc.annotation.RowModel");

	private RowMapperFactory() {
	}

	/** Metadata for a single {@code @RowModel} record field. */
	public record FieldMeta(String name, String typeName) {
	}

	/** Metadata for a {@code @RowModel} record. */
	public record RowModelMeta(String modelClassName, String packageName, String simpleName, String tableName,
			List<FieldMeta> fields) {
	}

	/**
	 * Scans a Jandex index for {@code @RowModel} records and extracts field
	 * metadata suitable for code generation or reflective mapping.
	 */
	public static List<RowModelMeta> scanJandex(IndexView index) {
		List<RowModelMeta> result = new ArrayList<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || ci.isInterface()) {
				continue;
			}
			if (!ci.hasAnnotation(ROW_MODEL_DOT)) {
				continue;
			}
			List<RecordComponentInfo> components = ci.recordComponents();
			if (components == null || components.isEmpty()) {
				continue;
			}

			List<FieldMeta> fields = new ArrayList<>();
			for (RecordComponentInfo comp : components) {
				fields.add(new FieldMeta(comp.name(), comp.type().name().toString()));
			}

			// Jandex returns null (not the annotation default) when table is unset,
			// so guard against it explicitly; an empty table fails fast at assembly.
			AnnotationValue tableValue = ci.classAnnotation(ROW_MODEL_DOT).value("table");
			String tableName = tableValue != null ? tableValue.asString() : "";
			if (tableName == null || tableName.isBlank()) {
				throw new IllegalStateException("@RowModel on " + ci.name()
						+ " must declare a non-empty table() — the physical table name is required.");
			}

			result.add(new RowModelMeta(ci.name().toString(), ci.name().packagePrefix(),
					ci.name().withoutPackagePrefix(), tableName, fields));
		}
		return result;
	}

	/**
	 * Creates a reflective {@code RowMapper} at runtime using Jackson
	 * {@code ObjectMapper}. Used by the runtime DI engine when no code generation
	 * is available.
	 */
	@SuppressWarnings("unchecked")
	public static <T> RowMapper<T> createReflective(Class<T> modelClass, RowModelMeta meta) {
		return new ReflectiveRowMapper<>(modelClass, meta);
	}

	/**
	 * Convenience overload that resolves the model {@link Class} from the metadata
	 * before delegating to {@link #createReflective(Class, RowModelMeta)}. Used by
	 * the {@link ReflectiveRowMapperRegistrar} component, which discovers
	 * {@code @RowModel} records through the Jandex index and loads each model class
	 * within this module (loading a user-declared model is a data-module
	 * responsibility, not a cross-module reflection).
	 */
	@SuppressWarnings("unchecked")
	public static RowMapper<?> createReflective(RowModelMeta meta) {
		try {
			Class<?> modelClass = Class.forName(meta.modelClassName());
			return new ReflectiveRowMapper<>(modelClass, meta);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Cannot load @RowModel class: " + meta.modelClassName(), e);
		}
	}

	/**
	 * Converts a camelCase field name to a snake_case column name, the standard SQL
	 * naming convention. Example: {@code createdAt} becomes {@code created_at}.
	 */
	public static String camelToSnake(String camelCase) {
		StringBuilder sb = new StringBuilder(camelCase.length() + 4);
		for (int i = 0; i < camelCase.length(); i++) {
			char ch = camelCase.charAt(i);
			if (Character.isUpperCase(ch)) {
				if (i > 0) {
					sb.append('_');
				}
				sb.append(Character.toLowerCase(ch));
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

	private static final class ReflectiveRowMapper<T> implements RowMapper<T> {
		private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
		private final Class<T> modelClass;
		private final String[] fieldNames;
		private final String[] columnNames;
		private final Class<?>[] fieldTypes;

		ReflectiveRowMapper(Class<T> modelClass, RowModelMeta meta) {
			this.modelClass = modelClass;
			List<String> fields = new ArrayList<>();
			List<String> cols = new ArrayList<>();
			List<Class<?>> types = new ArrayList<>();
			for (FieldMeta f : meta.fields()) {
				fields.add(f.name());
				cols.add(camelToSnake(f.name()));
				types.add(resolveFieldType(f.typeName()));
			}
			this.fieldNames = fields.toArray(String[]::new);
			this.columnNames = cols.toArray(String[]::new);
			this.fieldTypes = types.toArray(Class<?>[]::new);
		}

		@Override
		public T mapRow(ResultSet rs, int rowNum) throws SQLException {
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				values.put(fieldNames[i], rs.getObject(columnNames[i], fieldTypes[i]));
			}
			return MAPPER.convertValue(values, modelClass);
		}
	}

	/**
	 * Maps a {@code @RowModel} field type name to the Java type used for JDBC's
	 * native {@code ResultSet.getObject(col, type)} read. Only JDBC-native types
	 * are supported; anything else fails fast so an unsupported mapping never
	 * reaches row-mapping time. Public so the AOT engine can reuse the exact same
	 * type contract when emitting inline mappers.
	 */
	public static Class<?> resolveFieldType(String typeName) {
		return switch (typeName) {
			case "int", "java.lang.Integer" -> Integer.class;
			case "long", "java.lang.Long" -> Long.class;
			case "double", "java.lang.Double" -> Double.class;
			case "boolean", "java.lang.Boolean" -> Boolean.class;
			case "java.lang.String" -> String.class;
			case "java.math.BigDecimal" -> java.math.BigDecimal.class;
			case "java.util.UUID" -> java.util.UUID.class;
			case "java.time.LocalDateTime" -> java.time.LocalDateTime.class;
			case "java.time.LocalDate" -> java.time.LocalDate.class;
			case "java.time.LocalTime" -> java.time.LocalTime.class;
			case "java.time.OffsetDateTime" -> java.time.OffsetDateTime.class;
			default -> throw new IllegalStateException("Unsupported @RowModel field type: " + typeName
					+ ". @RowModel supports JDBC-native types only (primitives, String, BigDecimal, UUID, "
					+ "java.time.*). Complex types such as jsonb or nested records require explicit extension.");
		};
	}

	/**
	 * Builds the JDBC-native read expression emitted by the AOT engine for a field,
	 * e.g. {@code rs.getObject("created_at", LocalDateTime.class)}. Keeps the
	 * generated mapper aligned with the reflective one (same type contract, no
	 * Jackson fallback).
	 */
	public static String jdbcReadExpression(String columnName, String typeName) {
		Class<?> type = resolveFieldType(typeName);
		return "rs.getObject(\"" + columnName + "\", " + type.getCanonicalName() + ".class)";
	}

	/**
	 * Fails fast at assembly if any {@code @RowModel} field uses a type the
	 * reflective mapper cannot map, so an unsupported mapping surfaces as a clear
	 * error rather than a runtime row-mapping surprise.
	 */
	public static void assertSupported(RowModelMeta meta) {
		for (FieldMeta f : meta.fields()) {
			resolveFieldType(f.typeName());
		}
	}
}
