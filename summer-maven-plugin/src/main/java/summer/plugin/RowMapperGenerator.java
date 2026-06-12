package summer.plugin;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;

/**
 * Generates {@code RowMapper} classes for {@code @RowModel} annotated records.
 *
 * <p>
 * For a record like:
 * </p>
 *
 * <pre>{@code
 * @RowModel
 * public record User(long id, String name, String email) {
 * }
 * }</pre>
 *
 * <p>
 * This generator creates:
 * </p>
 *
 * <pre>{@code
 * public class User_RowMapper implements RowMapper<User> {
 * 	@Override
 * 	public User mapRow(ResultSet rs, int rowNum) throws SQLException {
 * 		return new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"));
 * 	}
 * }
 * }</pre>
 *
 * <p>
 * It also generates a {@code RowMapperConfiguration} class that registers all
 * mappers with {@code RowMapperRegistry}.
 * </p>
 */
final class RowMapperGenerator {
	private static final DotName ROW_MODEL_DOT = DotName.createSimple("summer.data.jdbc.annotation.RowModel");
	private static final ClassName ROW_MAPPER = ClassName.get("summer.data.jdbc", "RowMapper");
	private static final ClassName ROW_MAPPER_REGISTRY = ClassName.get("summer.data.jdbc", "RowMapperRegistry");
	private static final ClassName RESULT_SET = ClassName.get("java.sql", "ResultSet");
	private static final ClassName SQL_EXCEPTION = ClassName.get("java.sql", "SQLException");
	private static final ClassName CONFIGURATION = ClassName.get("summer.core.annotation", "Configuration");
	private static final ClassName BEAN = ClassName.get("summer.core.annotation", "Bean");

	private static final DotName ROW_MAPPER_REGISTRY_DOT = DotName.createSimple("summer.data.jdbc.RowMapperRegistry");

	void generate(IndexView index, File outputDir) throws IOException {
		List<ClassInfo> rowModels = new ArrayList<>();
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || ci.isInterface())
				continue;

			if (!ci.hasAnnotation(ROW_MODEL_DOT))
				continue;
			rowModels.add(ci);
			generateMapper(ci, outputDir);
		}
		if (!rowModels.isEmpty() && !hasUserDefinedRowMapperRegistryBean(index)) {
			generateConfiguration(rowModels, outputDir);
		}
	}

	/**
	 * Checks if any {@code @Configuration} class in the index already declares a
	 * {@code @Bean} method returning {@code RowMapperRegistry}. If so, we skip
	 * generating {@code RowMapperConfiguration} to avoid duplicates.
	 */
	private boolean hasUserDefinedRowMapperRegistryBean(IndexView index) {
		ClassName configAnn = ClassName.get("summer.core.annotation", "Configuration");
		ClassName beanAnn = ClassName.get("summer.core.annotation", "Bean");
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || ci.isInterface() || ci.isAbstract())
				continue;
			if (!ci.hasAnnotation(DotName.createSimple(configAnn.canonicalName())))
				continue;
			for (MethodInfo method : ci.methods()) {
				if (!method.hasAnnotation(DotName.createSimple(beanAnn.canonicalName())))
					continue;
				if (method.returnType() != null && method.returnType().name().equals(ROW_MAPPER_REGISTRY_DOT)) {
					return true;
				}
			}
		}
		return false;
	}

	private void generateMapper(ClassInfo ci, File outputDir) throws IOException {
		String packageName = ci.name().packagePrefix();
		String simpleName = ci.name().withoutPackagePrefix();
		String mapperClassName = simpleName + "_RowMapper";

		ClassName modelClass = ClassName.get(packageName, simpleName);
		TypeName genericMapper = ParameterizedTypeName.get(ROW_MAPPER, modelClass);

		// Build mapRow method
		MethodSpec.Builder mapRowMethod = MethodSpec.methodBuilder("mapRow").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(modelClass).addParameter(RESULT_SET, "rs")
				.addParameter(int.class, "rowNum").addException(SQL_EXCEPTION);

		List<RecordComponentInfo> components = ci.recordComponents();
		if (components == null || components.isEmpty()) {
			// Not a record — skip with warning
			return;
		}

		// Read each column into a named variable
		for (RecordComponentInfo comp : components) {
			com.palantir.javapoet.TypeName compType = toTypeName(comp.type());
			mapRowMethod.addStatement("$T $N = $L", compType, comp.name(),
					CodeBlock.of(mapColumnReader(comp.type(), comp.name())));
		}

		// Construct record via Jackson to decouple from Jandex component order
		mapRowMethod.addStatement("$T values = new $T<>()", java.util.Map.class, java.util.HashMap.class);
		for (RecordComponentInfo comp : components) {
			mapRowMethod.addStatement("values.put($S, $N)", comp.name(), comp.name());
		}
		mapRowMethod.addStatement("return MAPPER.convertValue(values, $T.class)", modelClass);

		com.palantir.javapoet.FieldSpec mapperField = com.palantir.javapoet.FieldSpec
				.builder(com.fasterxml.jackson.databind.ObjectMapper.class, "MAPPER",
						javax.lang.model.element.Modifier.PRIVATE, javax.lang.model.element.Modifier.STATIC,
						javax.lang.model.element.Modifier.FINAL)
				.initializer("new $T()", com.fasterxml.jackson.databind.ObjectMapper.class).build();

		TypeSpec mapperClass = TypeSpec.classBuilder(mapperClassName).addModifiers(Modifier.PUBLIC)
				.addField(mapperField).addSuperinterface(genericMapper).addMethod(mapRowMethod.build()).build();

		JavaFile javaFile = JavaFile.builder(packageName, mapperClass).indent("    ").build();

		javaFile.writeTo(outputDir);
	}

	private void generateConfiguration(List<ClassInfo> rowModels, File outputDir) throws IOException {
		String configPackage = rowModels.get(0).name().packagePrefix();

		MethodSpec.Builder method = MethodSpec.methodBuilder("rowMapperRegistry").addAnnotation(BEAN)
				.addModifiers(Modifier.PUBLIC).returns(ROW_MAPPER_REGISTRY);

		method.addStatement("$T registry = new $T()", ROW_MAPPER_REGISTRY, ROW_MAPPER_REGISTRY);
		for (ClassInfo ci : rowModels) {
			String simpleName = ci.name().withoutPackagePrefix();
			ClassName modelClass = ClassName.get(ci.name().packagePrefix(), simpleName);
			ClassName mapperClass = ClassName.get(ci.name().packagePrefix(), simpleName + "_RowMapper");
			method.addStatement("registry.put($T.class, new $T())", modelClass, mapperClass);
		}
		method.addStatement("return registry");

		TypeSpec configClass = TypeSpec.classBuilder("RowMapperConfiguration").addAnnotation(CONFIGURATION)
				.addModifiers(Modifier.PUBLIC).addMethod(method.build()).build();

		JavaFile.builder(configPackage, configClass)
				.addFileComment("Auto-generated by summer-maven-plugin. Do not edit!").indent("    ").build()
				.writeTo(outputDir);
	}

	private static String mapColumnReader(Type type, String name) {
		String typeName = type.name().toString();
		return switch (typeName) {
			case "int", "java.lang.Integer" -> "rs.getInt(\"" + name + "\")";
			case "long", "java.lang.Long" -> "rs.getLong(\"" + name + "\")";
			case "double", "java.lang.Double" -> "rs.getDouble(\"" + name + "\")";
			case "boolean", "java.lang.Boolean" -> "rs.getBoolean(\"" + name + "\")";
			case "java.lang.String" -> "rs.getString(\"" + name + "\")";
			default -> "(" + typeName + ") rs.getObject(\"" + name + "\")";
		};
	}

	private static com.palantir.javapoet.TypeName toTypeName(Type type) {
		return switch (type.name().toString()) {
			case "int" -> com.palantir.javapoet.TypeName.INT;
			case "long" -> com.palantir.javapoet.TypeName.LONG;
			case "double" -> com.palantir.javapoet.TypeName.DOUBLE;
			case "boolean" -> com.palantir.javapoet.TypeName.BOOLEAN;
			case "float" -> com.palantir.javapoet.TypeName.FLOAT;
			case "short" -> com.palantir.javapoet.TypeName.SHORT;
			case "byte" -> com.palantir.javapoet.TypeName.BYTE;
			case "char" -> com.palantir.javapoet.TypeName.CHAR;
			default -> ClassName.bestGuess(type.name().toString());
		};
	}
}
