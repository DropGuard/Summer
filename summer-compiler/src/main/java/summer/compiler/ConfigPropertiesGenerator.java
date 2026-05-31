package summer.compiler;

import com.palantir.javapoet.*;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import summer.core.Component;
import summer.core.Provider;
import summer.core.config.ConfigurationProperties;

/**
 * Generates Provider classes for {@code @ConfigurationProperties} annotated
 * records.
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

	private static final ClassName CONFIGURATION_BINDER = ClassName.get("summer.core.config", "ConfigurationBinder");

	private ConfigPropertiesGenerator() {
	}

	/**
	 * Generates a Provider class for the given {@code @ConfigurationProperties}
	 * annotated type.
	 *
	 * @param configClass
	 *            the annotated type
	 * @param processingEnv
	 *            the processing environment
	 * @return true if a new type was generated
	 */
	static boolean generate(TypeElement configClass, ProcessingEnvironment processingEnv) {
		ConfigurationProperties ann = configClass.getAnnotation(ConfigurationProperties.class);
		if (ann == null) {
			return false;
		}

		String packageName = processingEnv.getElementUtils().getPackageOf(configClass).getQualifiedName().toString();
		String providerClassName = configClass.getSimpleName() + "_ConfigPropertiesProvider";
		String prefix = ann.prefix();

		ClassName configClassName = ClassName.get(configClass);
		ClassName providerInterface = ClassName.get(Provider.class);

		// Build the provide() method
		MethodSpec.Builder provideMethod = MethodSpec.methodBuilder("provide").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(configClassName);

		if (prefix == null || prefix.isEmpty()) {
			provideMethod.addStatement("return $T.bind($S, $T.class)", CONFIGURATION_BINDER, "application.yml",
					configClassName);
		} else {
			provideMethod.addStatement("return $T.bind($S, $T.class, $S)", CONFIGURATION_BINDER, "application.yml",
					configClassName, prefix);
		}

		// Build the Provider class
		TypeSpec providerClass = TypeSpec.classBuilder(providerClassName).addModifiers(Modifier.PUBLIC, Modifier.FINAL)
				.addAnnotation(Component.class)
				.addSuperinterface(ParameterizedTypeName.get(providerInterface, configClassName))
				.addMethod(provideMethod.build()).build();

		JavaFile javaFile = JavaFile.builder(packageName, providerClass).build();

		try {
			javaFile.writeTo(processingEnv.getFiler());
			processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
					"[Summer AOT] Generated " + packageName + "." + providerClassName);
			return true;
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"Failed to generate config properties provider: " + e.getMessage());
			return false;
		}
	}
}
