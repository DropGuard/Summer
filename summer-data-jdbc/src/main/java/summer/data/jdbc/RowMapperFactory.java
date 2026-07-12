package summer.data.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;

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
	public record FieldMeta(String name, String typeName, String jdbcGetter) {
	}

	/** Metadata for a {@code @RowModel} record. */
	public record RowModelMeta(String modelClassName, String packageName, String simpleName, List<FieldMeta> fields) {
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
				fields.add(new FieldMeta(comp.name(), comp.type().name().toString(),
						jdbcGetter(comp.type(), comp.name())));
			}

			result.add(new RowModelMeta(ci.name().toString(), ci.name().packagePrefix(),
					ci.name().withoutPackagePrefix(), fields));
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

	private static String jdbcGetter(Type type, String fieldName) {
		String colName = camelToSnake(fieldName);
		return switch (type.name().toString()) {
			case "int", "java.lang.Integer" -> "rs.getInt(\"" + colName + "\")";
			case "long", "java.lang.Long" -> "rs.getLong(\"" + colName + "\")";
			case "double", "java.lang.Double" -> "rs.getDouble(\"" + colName + "\")";
			case "boolean", "java.lang.Boolean" -> "rs.getBoolean(\"" + colName + "\")";
			case "java.lang.String" -> "rs.getString(\"" + colName + "\")";
			default -> "(" + type.name().toString() + ") rs.getObject(\"" + colName + "\")";
		};
	}

	/**
	 * Converts a camelCase field name to a snake_case column name, the standard
	 * SQL naming convention. Example: {@code createdAt} becomes {@code created_at}.
	 */
	static String camelToSnake(String camelCase) {
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
		private static final ObjectMapper MAPPER = new ObjectMapper()
				.findAndRegisterModules();
		private final Class<T> modelClass;
		private final String[] fieldNames;
		private final String[] columnNames;

		ReflectiveRowMapper(Class<T> modelClass, RowModelMeta meta) {
			this.modelClass = modelClass;
			List<String> fields = new ArrayList<>();
			List<String> cols = new ArrayList<>();
			for (FieldMeta f : meta.fields()) {
				fields.add(f.name());
				cols.add(camelToSnake(f.name()));
			}
			this.fieldNames = fields.toArray(String[]::new);
			this.columnNames = cols.toArray(String[]::new);
		}

		@Override
		public T mapRow(ResultSet rs, int rowNum) throws SQLException {
			Map<String, Object> values = new HashMap<>();
			for (int i = 0; i < fieldNames.length; i++) {
				values.put(fieldNames[i], rs.getObject(columnNames[i]));
			}
			return MAPPER.convertValue(values, modelClass);
		}
	}
}
