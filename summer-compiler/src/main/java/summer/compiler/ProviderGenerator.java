package summer.compiler;

import com.palantir.javapoet.*;
import java.io.IOException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import summer.core.Component;
import summer.core.Provider;
import summer.core.annotation.Bean;

final class ProviderGenerator {

	private ProviderGenerator() {
	}

	static boolean generate(TypeElement configClass, ProcessingEnvironment processingEnv) {
		String packageName = processingEnv.getElementUtils().getPackageOf(configClass).getQualifiedName().toString()
				+ ".generated";

		boolean generated = false;
		for (Element enclosed : configClass.getEnclosedElements()) {
			if (enclosed.getKind() == ElementKind.METHOD && enclosed.getAnnotation(Bean.class) != null) {
				ExecutableElement method = (ExecutableElement) enclosed;
				if (generateSingle(packageName, configClass, method, processingEnv)) {
					generated = true;
				}
			}
		}
		return generated;
	}

	private static boolean generateSingle(String packageName, TypeElement configClass, ExecutableElement method,
			ProcessingEnvironment processingEnv) {
		TypeMirror returnType = method.getReturnType();
		String methodName = method.getSimpleName().toString();
		String providerClassName = configClass.getSimpleName() + "_" + capitalize(methodName) + "_Provider";

		TypeSpec.Builder providerSpecBuilder = TypeSpec.classBuilder(providerClassName)
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL).addAnnotation(Component.class)
				.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Provider.class), TypeName.get(returnType)))
				.addField(TypeName.get(configClass.asType()), "config", Modifier.PRIVATE, Modifier.FINAL);

		MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(TypeName.get(configClass.asType()), "config").addStatement("this.config = config");

		StringBuilder methodCallArgs = new StringBuilder();

		for (VariableElement param : method.getParameters()) {
			String paramName = param.getSimpleName().toString();
			TypeName paramType = TypeName.get(param.asType());

			providerSpecBuilder.addField(paramType, paramName, Modifier.PRIVATE, Modifier.FINAL);
			constructorBuilder.addParameter(paramType, paramName);
			constructorBuilder.addStatement("this.$N = $N", paramName, paramName);

			if (methodCallArgs.length() > 0) {
				methodCallArgs.append(", ");
			}
			methodCallArgs.append("this.").append(paramName);
		}

		providerSpecBuilder.addMethod(constructorBuilder.build());

		providerSpecBuilder.addMethod(MethodSpec.methodBuilder("provide").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(TypeName.get(returnType))
				.addStatement("return config.$L(" + methodCallArgs.toString() + ")", methodName).build());

		JavaFile javaFile = JavaFile.builder(packageName, providerSpecBuilder.build()).build();

		try {
			javaFile.writeTo(processingEnv.getFiler());
			return true;
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR,
					"Failed to generate provider: " + e.getMessage());
			return false;
		}
	}

	private static String capitalize(String str) {
		if (str == null || str.isEmpty())
			return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
