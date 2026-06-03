package summer.compiler;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;

final class RowMapperGenerator {

	private RowMapperGenerator() {
	}

	static boolean generate(TypeElement rowModelElement, ProcessingEnvironment processingEnv) {
		String packageName = processingEnv.getElementUtils().getPackageOf(rowModelElement).getQualifiedName()
				.toString();
		String className = rowModelElement.getSimpleName().toString() + "_RowMapper";

		ClassName rowModelClass = ClassName.get(rowModelElement);
		ClassName rowMapperInterface = ClassName.get("summer.data.jdbc", "RowMapper");
		TypeName genericRowMapper = ParameterizedTypeName.get(rowMapperInterface, rowModelClass);

		TypeSpec.Builder mapperBuilder = TypeSpec.classBuilder(className).addModifiers(Modifier.PUBLIC)
				.addSuperinterface(genericRowMapper);

		MethodSpec.Builder mapRowMethod = MethodSpec.methodBuilder("mapRow").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(rowModelClass)
				.addParameter(ClassName.get("java.sql", "ResultSet"), "rs").addParameter(int.class, "rowNum")
				.addException(ClassName.get("java.sql", "SQLException"));

		if (rowModelElement.getKind() == ElementKind.RECORD) {
			List<? extends RecordComponentElement> recordComponents = rowModelElement.getRecordComponents();
			mapRowMethod.addStatement("return new $T(" + buildRecordArgs(recordComponents) + ")", rowModelClass);
		} else {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"@RowModel is currently only supported on Java Records", rowModelElement);
			return false;
		}

		mapperBuilder.addMethod(mapRowMethod.build());

		JavaFile javaFile = JavaFile.builder(packageName, mapperBuilder.build()).build();
		try {
			javaFile.writeTo(processingEnv.getFiler());
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static String buildRecordArgs(List<? extends RecordComponentElement> recordComponents) {
		StringBuilder args = new StringBuilder();
		for (RecordComponentElement comp : recordComponents) {
			if (args.length() > 0)
				args.append(", ");
			String name = comp.getSimpleName().toString();
			String type = comp.asType().toString();
			args.append(mapColumnReader(type, name));
		}
		return args.toString();
	}

	private static String mapColumnReader(String type, String name) {
		if (type.equals("int") || type.equals("java.lang.Integer")) {
			return "rs.getInt(\"" + name + "\")";
		} else if (type.equals("long") || type.equals("java.lang.Long")) {
			return "rs.getLong(\"" + name + "\")";
		} else if (type.equals("double") || type.equals("java.lang.Double")) {
			return "rs.getDouble(\"" + name + "\")";
		} else if (type.equals("boolean") || type.equals("java.lang.Boolean")) {
			return "rs.getBoolean(\"" + name + "\")";
		} else if (type.equals("java.lang.String")) {
			return "rs.getString(\"" + name + "\")";
		} else {
			return "(" + type + ") rs.getObject(\"" + name + "\")";
		}
	}
}
