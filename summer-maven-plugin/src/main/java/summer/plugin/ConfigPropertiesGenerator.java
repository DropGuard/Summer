package summer.plugin;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import javax.lang.model.element.Modifier;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Generates {@code Provider} classes for {@code @ConfigurationProperties}
 * annotated records.
 *
 * <p>
 * For a record like:
 * </p>
 *
 * <pre>{@code
 * @ConfigurationProperties(prefix = "jwt")
 * public record JwtProperties(String secret, long expiration) {
 * }
 * }</pre>
 *
 * <p>
 * This generator creates:
 * </p>
 *
 * <pre>
 * {
 * 	&#64;code
 * 	&#64;Component
 * 	public class JwtProperties_ConfigPropertiesProvider implements Provider<JwtProperties> {
 * 		@Override
 * 		public JwtProperties provide() {
 * 			return ConfigurationBinder.bind("application.yml", JwtProperties.class, "jwt");
 * 		}
 * 	}
 * }
 * </pre>
 */
final class ConfigPropertiesGenerator {

	private static final DotName CONFIG_PROPERTIES_DOT = DotName
			.createSimple("summer.core.config.ConfigurationProperties");
	private static final ClassName CONFIGURATION_BINDER = ClassName.get("summer.core.config", "ConfigurationBinder");
	private static final ClassName PROVIDER = ClassName.get("summer.core", "Provider");
	private static final ClassName COMPONENT = ClassName.get("summer.core", "Component");

	void generate(IndexView index, File outputDir) throws IOException {
		for (ClassInfo ci : index.getKnownClasses()) {
			if (ci.isAnnotation() || ci.isInterface())
				continue;

			AnnotationInstance ann = ci.annotation(CONFIG_PROPERTIES_DOT);
			if (ann == null)
				continue;

			generateProvider(ci, ann, outputDir);
		}
	}

	private void generateProvider(ClassInfo ci, AnnotationInstance ann, File outputDir) throws IOException {
		String packageName = ci.name().packagePrefix();
		String simpleName = ci.name().withoutPackagePrefix();
		String providerClassName = simpleName + "_ConfigPropertiesProvider";

		ClassName configClass = ClassName.get(packageName, simpleName);

		// Read prefix value
		String prefix = "";
		if (ann.value() != null) {
			prefix = ann.value().asString();
		}

		// Build provide() method
		MethodSpec.Builder provideMethod = MethodSpec.methodBuilder("provide").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(configClass);

		if (prefix.isEmpty()) {
			provideMethod.addStatement("return $T.bind($S, $T.class)", CONFIGURATION_BINDER, "application.yml",
					configClass);
		} else {
			provideMethod.addStatement("return $T.bind($S, $T.class, $S)", CONFIGURATION_BINDER, "application.yml",
					configClass, prefix);
		}

		// Build the Provider class
		TypeSpec providerClass = TypeSpec.classBuilder(providerClassName).addModifiers(Modifier.PUBLIC, Modifier.FINAL)
				.addAnnotation(COMPONENT).addSuperinterface(ParameterizedTypeName.get(PROVIDER, configClass))
				.addMethod(provideMethod.build()).build();

		JavaFile javaFile = JavaFile.builder(packageName, providerClass).indent("    ").build();

		javaFile.writeTo(outputDir);
	}
}
