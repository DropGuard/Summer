package summer.plugin;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
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
 * public record User(long id, String name, String email) {}
 * }</pre>
 *
 * <p>
 * This generator creates:
 * </p>
 *
 * <pre>{@code
 * public class User_RowMapper implements RowMapper<User> {
 *     @Override
 *     public User mapRow(ResultSet rs, int rowNum) throws SQLException {
 *         return new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"));
 *     }
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

        if (!rowModels.isEmpty()) {
            generateConfiguration(rowModels, outputDir);
        }
    }

    private void generateMapper(ClassInfo ci, File outputDir) throws IOException {
        String packageName = ci.name().packagePrefix();
        String simpleName = ci.name().withoutPackagePrefix();
        String mapperClassName = simpleName + "_RowMapper";

        ClassName modelClass = ClassName.get(packageName, simpleName);
        TypeName genericMapper = ParameterizedTypeName.get(ROW_MAPPER, modelClass);

        // Build mapRow method
        MethodSpec.Builder mapRowMethod = MethodSpec.methodBuilder("mapRow")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(modelClass)
                .addParameter(RESULT_SET, "rs")
                .addParameter(int.class, "rowNum")
                .addException(SQL_EXCEPTION);

        List<RecordComponentInfo> components = ci.recordComponents();
        if (components == null || components.isEmpty()) {
            // Not a record — skip with warning
            return;
        }

        StringBuilder args = new StringBuilder();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0)
                args.append(", ");
            RecordComponentInfo comp = components.get(i);
            args.append(mapColumnReader(comp.type(), comp.name()));
        }

        mapRowMethod.addStatement("return new $T(" + args + ")", modelClass);

        TypeSpec mapperClass = TypeSpec.classBuilder(mapperClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(genericMapper)
                .addMethod(mapRowMethod.build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, mapperClass)
                .indent("    ")
                .build();

        javaFile.writeTo(outputDir);
    }

    private void generateConfiguration(List<ClassInfo> rowModels, File outputDir) throws IOException {
        // Use the package of the first RowModel for the configuration class
        String packageName = rowModels.get(0).name().packagePrefix();

        MethodSpec.Builder registerMethod = MethodSpec.methodBuilder("rowMapperRegistry")
                .addAnnotation(BEAN)
                .addModifiers(Modifier.PUBLIC)
                .returns(ROW_MAPPER_REGISTRY);

        registerMethod.addStatement("$T registry = new $T()", ROW_MAPPER_REGISTRY, ROW_MAPPER_REGISTRY);

        for (ClassInfo ci : rowModels) {
            String simpleName = ci.name().withoutPackagePrefix();
            ClassName modelClass = ClassName.get(ci.name().packagePrefix(), simpleName);
            ClassName mapperClass = ClassName.get(ci.name().packagePrefix(), simpleName + "_RowMapper");
            registerMethod.addStatement("registry.register($T.class, new $T())", modelClass, mapperClass);
        }

        registerMethod.addStatement("return registry");

        TypeSpec configClass = TypeSpec.classBuilder("RowMapperConfiguration")
                .addAnnotation(CONFIGURATION)
                .addModifiers(Modifier.PUBLIC)
                .addMethod(registerMethod.build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, configClass)
                .addFileComment("Auto-generated by summer-maven-plugin. Do not edit!")
                .indent("    ")
                .build();

        javaFile.writeTo(outputDir);
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
}
