package summer.plugin;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Generates RowMapper implementations for @RowModel annotated records.
 * 
 * <p>
 * This runs in summer-maven-plugin with full classpath access, complementing
 * summer-compiler's APT-based generation.
 * </p>
 */
public final class RowMapperGenerator {

	private static final String ROW_MODEL_ANNOTATION = "summer.data.jdbc.annotation.RowModel";

	RowMapperGenerator() {
	}

	/**
	 * Generate RowMapper classes for all @RowModel annotated records.
	 * 
	 * @param index
	 *            the Jandex index
	 * @param outputDir
	 *            directory to write generated source files
	 */
	public void generate(IndexView index, java.io.File outputDir) throws IOException {
		ClassInfo rowModelAnnotation = index
				.getClassByName(org.jboss.jandex.DotName.createSimple(ROW_MODEL_ANNOTATION));
		if (rowModelAnnotation == null) {
			return;
		}

		int count = 0;
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || ci.isInterface()) {
				continue;
			}
			if (ci.hasAnnotation(org.jboss.jandex.DotName.createSimple(ROW_MODEL_ANNOTATION))) {
				if (ci.isRecord()) {
					generateMapper(ci, outputDir);
					count++;
				}
			}
		}
	}

	private void generateMapper(ClassInfo recordClass, java.io.File outputDir) throws IOException {
		String packageName = recordClass.name().packagePrefix();
		String simpleName = recordClass.name().withoutPackagePrefix();
		String mapperName = simpleName + "_RowMapper";

		ClassName rowModelClass = ClassName.get(packageName, simpleName);
		ClassName rowMapperInterface = ClassName.get("summer.data.jdbc", "RowMapper");
		TypeName genericRowMapper = ParameterizedTypeName.get(rowMapperInterface, rowModelClass);

		// Build mapRow method
		MethodSpec.Builder mapRowMethod = MethodSpec.methodBuilder("mapRow").addAnnotation(Override.class)
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC).returns(rowModelClass)
				.addParameter(ClassName.get("java.sql", "ResultSet"), "rs").addParameter(int.class, "rowNum")
				.addException(ClassName.get("java.sql", "SQLException"));

		// Get record components from the record's constructor
		MethodInfo ctor = null;
		for (MethodInfo m : recordClass.methods()) {
			if (m.name().equals("<init>")) {
				ctor = m;
				break;
			}
		}
		if (ctor == null) {
			return;
		}

		StringBuilder args = new StringBuilder();
		for (int i = 0; i < ctor.parametersCount(); i++) {
			if (args.length() > 0) {
				args.append(", ");
			}
			String paramName = ctor.parameterName(i);
			Type paramType = ctor.parameterType(i);
			String typeName = paramType.name().toString();

			if (typeName.equals("int") || typeName.equals("java.lang.Integer")) {
				args.append("rs.getInt(\"").append(paramName).append("\")");
			} else if (typeName.equals("long") || typeName.equals("java.lang.Long")) {
				args.append("rs.getLong(\"").append(paramName).append("\")");
			} else if (typeName.equals("double") || typeName.equals("java.lang.Double")) {
				args.append("rs.getDouble(\"").append(paramName).append("\")");
			} else if (typeName.equals("boolean") || typeName.equals("java.lang.Boolean")) {
				args.append("rs.getBoolean(\"").append(paramName).append("\")");
			} else if (typeName.equals("java.lang.String")) {
				args.append("rs.getString(\"").append(paramName).append("\")");
			} else {
				args.append("(").append(typeName).append(") rs.getObject(\"").append(paramName).append("\")");
			}
		}
		mapRowMethod.addStatement("return new $T(" + args.toString() + ")", rowModelClass);

		TypeSpec mapperClass = TypeSpec.classBuilder(mapperName)
				.addModifiers(javax.lang.model.element.Modifier.PUBLIC, javax.lang.model.element.Modifier.FINAL)
				.addSuperinterface(genericRowMapper).addMethod(mapRowMethod.build()).build();

		JavaFile.builder(packageName, mapperClass).build().writeTo(outputDir);
	}
}
