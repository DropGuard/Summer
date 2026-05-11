package summer.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import summer.core.Component;
import summer.core.Provider;
import summer.core.annotation.Configuration;
import summer.core.annotation.Produces;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("summer.core.annotation.Configuration")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class SummerProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Configuration.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Configuration can only be applied to classes", element);
                continue;
            }

            TypeElement configClass = (TypeElement) element;
            processConfiguration(configClass);
        }
        return true;
    }

    private void processConfiguration(TypeElement configClass) {
        String packageName = processingEnv.getElementUtils().getPackageOf(configClass).getQualifiedName().toString();

        for (Element enclosed : configClass.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD && enclosed.getAnnotation(Produces.class) != null) {
                ExecutableElement method = (ExecutableElement) enclosed;
                generateProvider(packageName, configClass, method);
            }
        }
    }

    private void generateProvider(String packageName, TypeElement configClass, ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        String methodName = method.getSimpleName().toString();
        String providerClassName = configClass.getSimpleName() + "_" + capitalize(methodName) + "_Provider";

        // Build the Provider class using JavaPoet
        TypeSpec providerSpec = TypeSpec.classBuilder(providerClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(Component.class)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Provider.class), TypeName.get(returnType)))
                .addField(TypeName.get(configClass.asType()), "config", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.get(configClass.asType()), "config")
                        .addStatement("this.config = config")
                        .build())
                .addMethod(MethodSpec.methodBuilder("provide")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.get(returnType))
                        .addStatement("return config.$L()", methodName)
                        .build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, providerSpec).build();

        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate provider: " + e.getMessage());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
