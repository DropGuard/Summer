package summer.compiler;

import com.google.auto.service.AutoService;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import summer.core.annotation.Configuration;
import summer.core.config.ConfigurationProperties;

/**
 * Annotation processor for Summer framework.
 * 
 * <p>
 * This processor handles simple code generation tasks only:
 * </p>
 * <ul>
 * <li>RowMapper generation for @RowModel records</li>
 * <li>ConfigurationProperties generation</li>
 * <li>Provider generation for @Configuration classes</li>
 * </ul>
 * 
 * <p>
 * Bean discovery and AOT context generation have been moved to
 * {@code summer-maven-plugin} which has full classpath access.
 * </p>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({"summer.core.annotation.Configuration", "summer.data.jdbc.annotation.RowModel",
		"summer.core.config.ConfigurationProperties"})
public class SummerProcessor extends AbstractProcessor {

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			return false;
		}

		try {
			processConfigurations(roundEnv);
			processConfigurationProperties(roundEnv);
			processRowModels(roundEnv);
			return true;
		} catch (Exception e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"[Summer] Internal error: " + e.getMessage());
			return false;
		}
	}

	private void processConfigurations(RoundEnvironment roundEnv) {
		for (Element e : roundEnv.getElementsAnnotatedWith(Configuration.class)) {
			if (e.getKind() == ElementKind.CLASS) {
				TypeElement configClass = (TypeElement) e;
				ProviderGenerator.generate(configClass, processingEnv);
			}
		}
	}

	private void processConfigurationProperties(RoundEnvironment roundEnv) {
		for (Element e : roundEnv.getElementsAnnotatedWith(ConfigurationProperties.class)) {
			if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.CLASS) {
				TypeElement configType = (TypeElement) e;
				ConfigPropertiesGenerator.generate(configType, processingEnv);
			}
		}
	}

	private void processRowModels(RoundEnvironment roundEnv) {
		TypeElement rowModelType = processingEnv.getElementUtils()
				.getTypeElement("summer.data.jdbc.annotation.RowModel");
		if (rowModelType != null) {
			for (Element e : roundEnv.getElementsAnnotatedWith(rowModelType)) {
				if (e.getKind() == ElementKind.RECORD || e.getKind() == ElementKind.CLASS) {
					RowMapperGenerator.generate((TypeElement) e, processingEnv);
				}
			}
		}
	}
}
