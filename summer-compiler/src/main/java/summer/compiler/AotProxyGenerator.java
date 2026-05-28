package summer.compiler;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.*;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import summer.aop.Intercepts;

/**
 * Generates AOT proxy classes for beans that need interception. Extracted from
 * SummerProcessor to separate proxy code generation from bean collection.
 */
final class AotProxyGenerator {

	private AotProxyGenerator() {
	}

	/**
	 * Generates an AOT proxy class for the given bean. The proxy implements all
	 * interfaces of the target bean and delegates method calls through the
	 * interceptor chain without reflection.
	 */
	static void generate(BeanDefinition bean, ProcessingEnvironment processingEnv) {
		String packageName = processingEnv.getElementUtils().getPackageOf(bean.typeElement).getQualifiedName()
				.toString();
		String proxyClassName = bean.simpleName() + "$$AotProxy";

		TypeSpec.Builder proxyBuilder = TypeSpec.classBuilder(proxyClassName)
				.addAnnotation(
						AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

		for (TypeMirror iface : bean.interfaces) {
			proxyBuilder.addSuperinterface(TypeName.get(iface));
		}

		proxyBuilder.addField(ClassName.get(bean.typeElement), "target", Modifier.PRIVATE, Modifier.FINAL);

		ClassName interceptorType = ClassName.get("summer.aop", "MethodInterceptor");
		TypeName interceptorList = ParameterizedTypeName.get(ClassName.get(java.util.List.class), interceptorType);
		proxyBuilder.addField(interceptorList, "interceptors", Modifier.PRIVATE, Modifier.FINAL);

		// Constructor
		MethodSpec constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC)
				.addParameter(ClassName.get(bean.typeElement), "target").addParameter(interceptorList, "interceptors")
				.addStatement("this.target = target").addStatement("this.interceptors = interceptors").build();
		proxyBuilder.addMethod(constructor);

		// Find all interface methods and generate them
		Map<String, ProxyMethod> uniqueMethods = new LinkedHashMap<>();
		for (TypeMirror iface : bean.interfaces) {
			TypeElement ifaceElement = asTypeElement(iface, processingEnv);
			if (ifaceElement != null) {
				collectMethods(ifaceElement, ifaceElement, uniqueMethods);
			}
		}

		int methodIndex = 0;

		for (ProxyMethod pm : uniqueMethods.values()) {
			boolean isIntercepted = shouldInterceptMethod(bean.typeElement, pm.method, bean.interceptors,
					processingEnv);

			String targetFieldName = null;
			String interfaceFieldName = null;

			if (isIntercepted) {
				targetFieldName = pm.method.getSimpleName().toString() + "_" + methodIndex + "_targetMethod";
				interfaceFieldName = pm.method.getSimpleName().toString() + "_" + methodIndex + "_interfaceMethod";
				methodIndex++;

				TypeSpec.Builder metadataTarget = buildMethodMetadata(bean.typeElement, pm.method, processingEnv);

				proxyBuilder
						.addField(FieldSpec
								.builder(ClassName.get("summer.aop", "MethodMetadata"), targetFieldName,
										Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
								.initializer("$L", metadataTarget.build()).build());

				proxyBuilder
						.addField(FieldSpec
								.builder(ClassName.get("summer.aop", "MethodMetadata"), interfaceFieldName,
										Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
								.initializer("$L", metadataTarget.build()).build());
			}

			proxyBuilder
					.addMethod(buildProxyMethod(pm, isIntercepted, targetFieldName, interfaceFieldName, processingEnv));
		}

		// sneakyThrow
		proxyBuilder.addMethod(buildSneakyThrow());

		JavaFile proxyFile = JavaFile.builder(packageName, proxyBuilder.build()).build();
		try {
			proxyFile.writeTo(processingEnv.getFiler());
		} catch (IOException e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
					"Failed to write AOP proxy for: " + proxyClassName, bean.typeElement);
		}
	}

	// --- AOP analysis (used by SummerProcessor.analyzeAop) ---

	static List<TypeMirror> getInterceptsAnnotations(TypeElement element) {
		Intercepts intercepts = element.getAnnotation(Intercepts.class);
		if (intercepts == null)
			return Collections.emptyList();
		try {
			intercepts.annotations(); // triggers MirroredTypesException
			return Collections.emptyList();
		} catch (MirroredTypesException e) {
			return new ArrayList<>(e.getTypeMirrors());
		}
	}

	static boolean beanHasMethodsWithAnnotation(TypeElement bean, String annotationFqn) {
		for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
			if (AnnotationHelper.hasAnnotation(method, annotationFqn)) {
				return true;
			}
		}
		return false;
	}

	static boolean beanHasAnnotatedMethods(TypeElement bean, List<TypeMirror> targetAnnotations,
			ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		for (ExecutableElement method : ElementFilter.methodsIn(bean.getEnclosedElements())) {
			for (AnnotationMirror am : method.getAnnotationMirrors()) {
				for (TypeMirror target : targetAnnotations) {
					if (typeUtils.isSameType(am.getAnnotationType(), target)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// --- Private helpers ---

	private static TypeSpec.Builder buildMethodMetadata(TypeElement beanClass, ExecutableElement interfaceMethod,
			ProcessingEnvironment processingEnv) {
		TypeSpec.Builder metadataTarget = TypeSpec.anonymousClassBuilder("")
				.addSuperinterface(ClassName.get("summer.aop", "MethodMetadata"))
				.addMethod(MethodSpec.methodBuilder("getName").addAnnotation(Override.class)
						.addModifiers(Modifier.PUBLIC).returns(String.class)
						.addStatement("return $S", interfaceMethod.getSimpleName().toString()).build())
				.addMethod(MethodSpec.methodBuilder("getDeclaringClass").addAnnotation(Override.class)
						.addModifiers(Modifier.PUBLIC).returns(Class.class)
						.addStatement("return $T.class", ClassName.get(beanClass)).build());

		MethodSpec.Builder isPresent = MethodSpec.methodBuilder("isAnnotationPresent").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC).returns(boolean.class)
				.addParameter(ParameterizedTypeName.get(ClassName.get(Class.class),
						WildcardTypeName.subtypeOf(java.lang.annotation.Annotation.class)), "cls");

		MethodSpec.Builder getAnn = MethodSpec.methodBuilder("getAnnotation").addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addTypeVariable(TypeVariableName.get("T", java.lang.annotation.Annotation.class))
				.returns(TypeVariableName.get("T"))
				.addParameter(ParameterizedTypeName.get(ClassName.get(Class.class), TypeVariableName.get("T")), "cls");

		ExecutableElement actualMethod = findMatchingMethod(beanClass, interfaceMethod, processingEnv);
		if (actualMethod != null) {
			for (AnnotationMirror mirror : actualMethod.getAnnotationMirrors()) {
				TypeElement annType = (TypeElement) mirror.getAnnotationType().asElement();
				isPresent.beginControlFlow("if (cls == $T.class)", ClassName.get(annType));
				isPresent.addStatement("return true");
				isPresent.endControlFlow();

				getAnn.beginControlFlow("if (cls == $T.class)", ClassName.get(annType));

				TypeSpec.Builder annImpl = TypeSpec.anonymousClassBuilder("").addSuperinterface(ClassName.get(annType))
						.addMethod(MethodSpec.methodBuilder("annotationType").addAnnotation(Override.class)
								.addModifiers(Modifier.PUBLIC)
								.returns(ParameterizedTypeName.get(ClassName.get(Class.class),
										WildcardTypeName.subtypeOf(java.lang.annotation.Annotation.class)))
								.addStatement("return $T.class", ClassName.get(annType)).build());

				for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : processingEnv
						.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
					String attrName = entry.getKey().getSimpleName().toString();
					Object val = entry.getValue().getValue();
					String format = "return $L";
					if (val instanceof String) {
						format = "return $S";
					} else if (val instanceof VariableElement) {
						VariableElement ve = (VariableElement) val;
						val = ClassName.get((TypeElement) ve.getEnclosingElement()) + "." + ve.getSimpleName();
					} else if (val instanceof TypeMirror) {
						val = TypeName.get((TypeMirror) val) + ".class";
					} else if (val instanceof java.util.List) {
						java.util.List<?> list = (java.util.List<?>) val;
						StringBuilder arrayValues = new StringBuilder();
						TypeMirror returnTm = entry.getKey().getReturnType();
						TypeMirror erasedReturnTm = processingEnv.getTypeUtils().erasure(returnTm);
						TypeName returnType = TypeName.get(erasedReturnTm);
						arrayValues.append("new ").append(returnType.toString().replace("[]", "")).append("[]{");
						for (int i = 0; i < list.size(); i++) {
							Object elem = list.get(i);
							if (elem instanceof javax.lang.model.element.AnnotationValue) {
								Object innerVal = ((javax.lang.model.element.AnnotationValue) elem).getValue();
								if (i > 0)
									arrayValues.append(", ");
								if (innerVal instanceof String)
									arrayValues.append("\"").append(innerVal).append("\"");
								else if (innerVal instanceof VariableElement) {
									VariableElement ve = (VariableElement) innerVal;
									arrayValues.append(ClassName.get((TypeElement) ve.getEnclosingElement()))
											.append(".").append(ve.getSimpleName());
								} else if (innerVal instanceof TypeMirror) {
									TypeMirror tm = (TypeMirror) innerVal;
									arrayValues.append(TypeName.get(processingEnv.getTypeUtils().erasure(tm)))
											.append(".class");
								} else {
									arrayValues.append(innerVal);
								}
							}
						}
						arrayValues.append("}");
						val = arrayValues.toString();
					}

					annImpl.addMethod(MethodSpec.methodBuilder(attrName).addModifiers(Modifier.PUBLIC)
							.returns(TypeName.get(entry.getKey().getReturnType())).addStatement(format, val).build());
				}
				getAnn.addStatement("return (T) $L", annImpl.build());
				getAnn.endControlFlow();
			}
		}
		isPresent.addStatement("return false");
		getAnn.addStatement("return null");

		metadataTarget.addMethod(isPresent.build());
		metadataTarget.addMethod(getAnn.build());
		return metadataTarget;
	}

	private static MethodSpec buildProxyMethod(ProxyMethod pm, boolean isIntercepted, String targetFieldName,
			String interfaceFieldName, ProcessingEnvironment processingEnv) {
		MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(pm.method.getSimpleName().toString())
				.addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
				.returns(TypeName.get(pm.method.getReturnType()));

		for (VariableElement param : pm.method.getParameters()) {
			methodBuilder.addParameter(TypeName.get(param.asType()), param.getSimpleName().toString());
		}

		StringBuilder argsCall = new StringBuilder();
		for (int j = 0; j < pm.method.getParameters().size(); j++) {
			if (j > 0)
				argsCall.append(", ");
			argsCall.append(pm.method.getParameters().get(j).getSimpleName().toString());
		}

		if (isIntercepted) {
			methodBuilder.beginControlFlow("try");
			if (pm.method.getParameters().isEmpty()) {
				methodBuilder.addStatement("$T[] args = new $T[0]", Object.class, Object.class);
			} else {
				methodBuilder.addStatement("$T[] args = new $T[]{$L}", Object.class, Object.class, argsCall.toString());
			}

			String lambdaCode;
			if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
				lambdaCode = "() -> { this.target." + pm.method.getSimpleName().toString() + "(" + argsCall.toString()
						+ "); return null; }";
			} else {
				lambdaCode = "() -> { return this.target." + pm.method.getSimpleName().toString() + "("
						+ argsCall.toString() + "); }";
			}

			methodBuilder.addStatement("$T ctx = new $T(this.target, $N, $N, args, this.interceptors, $L)",
					ClassName.get("summer.aop", "AotInvocationContext"),
					ClassName.get("summer.aop", "AotInvocationContext"), targetFieldName, interfaceFieldName,
					lambdaCode);

			if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
				methodBuilder.addStatement("ctx.proceed()");
			} else {
				methodBuilder.addStatement("Object result = ctx.proceed()");
				TypeName returnType = TypeName.get(pm.method.getReturnType());
				TypeName castType = returnType.isPrimitive() ? returnType.box() : returnType;
				methodBuilder.addStatement("return ($T) result", castType);
			}

			methodBuilder.nextControlFlow("catch ($T t)", Throwable.class);
			methodBuilder.addStatement("throw sneakyThrow(t)");
			methodBuilder.endControlFlow();
		} else {
			if (pm.method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
				methodBuilder.addStatement("this.target.$N($L)", pm.method.getSimpleName().toString(),
						argsCall.toString());
			} else {
				methodBuilder.addStatement("return this.target.$N($L)", pm.method.getSimpleName().toString(),
						argsCall.toString());
			}
		}

		return methodBuilder.build();
	}

	private static MethodSpec buildSneakyThrow() {
		return MethodSpec.methodBuilder("sneakyThrow").addModifiers(Modifier.PRIVATE, Modifier.STATIC)
				.addTypeVariable(TypeVariableName.get("T", Throwable.class))
				.addAnnotation(
						AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "unchecked").build())
				.returns(RuntimeException.class).addParameter(Throwable.class, "t")
				.addException(TypeVariableName.get("T")).addStatement("throw (T) t").build();
	}

	private static boolean shouldInterceptMethod(TypeElement beanClass, ExecutableElement interfaceMethod,
			List<BeanDefinition> interceptorBeans, ProcessingEnvironment processingEnv) {
		ExecutableElement targetMethod = findMatchingMethod(beanClass, interfaceMethod, processingEnv);
		if (targetMethod == null)
			return false;

		if (AnnotationHelper.hasAnnotation(targetMethod, "summer.aop.Intercepted")) {
			return true;
		}

		for (BeanDefinition interceptor : interceptorBeans) {
			List<TypeMirror> targetAnnotations = getInterceptsAnnotations(interceptor.typeElement);
			for (AnnotationMirror am : targetMethod.getAnnotationMirrors()) {
				for (TypeMirror targetAnn : targetAnnotations) {
					if (processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), targetAnn)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static ExecutableElement findMatchingMethod(TypeElement beanClass, ExecutableElement interfaceMethod,
			ProcessingEnvironment processingEnv) {
		Types typeUtils = processingEnv.getTypeUtils();
		String name = interfaceMethod.getSimpleName().toString();
		List<TypeMirror> interfaceParamTypes = interfaceMethod.getParameters().stream().map(VariableElement::asType)
				.toList();

		for (ExecutableElement method : ElementFilter.methodsIn(beanClass.getEnclosedElements())) {
			if (method.getSimpleName().toString().equals(name)) {
				List<TypeMirror> targetParamTypes = method.getParameters().stream().map(VariableElement::asType)
						.toList();
				if (targetParamTypes.size() == interfaceParamTypes.size()) {
					boolean match = true;
					for (int i = 0; i < targetParamTypes.size(); i++) {
						if (!typeUtils.isSameType(typeUtils.erasure(targetParamTypes.get(i)),
								typeUtils.erasure(interfaceParamTypes.get(i)))) {
							match = false;
							break;
						}
					}
					if (match) {
						return method;
					}
				}
			}
		}
		return null;
	}

	private static void collectMethods(TypeElement interfaceElement, TypeElement originalInterface,
			Map<String, ProxyMethod> uniqueMethods) {
		for (Element e : interfaceElement.getEnclosedElements()) {
			if (e.getKind() == ElementKind.METHOD) {
				ExecutableElement method = (ExecutableElement) e;
				String sig = getMethodSignature(method);
				if (!uniqueMethods.containsKey(sig)) {
					ProxyMethod pm = new ProxyMethod();
					pm.method = method;
					pm.declaringInterface = originalInterface;
					uniqueMethods.put(sig, pm);
				}
			}
		}
		for (TypeMirror superI : interfaceElement.getInterfaces()) {
			TypeElement superEl = asTypeElement(superI, null);
			if (superEl != null) {
				collectMethods(superEl, originalInterface, uniqueMethods);
			}
		}
	}

	private static String getMethodSignature(ExecutableElement method) {
		StringBuilder sb = new StringBuilder();
		sb.append(method.getSimpleName().toString()).append("(");
		for (VariableElement p : method.getParameters()) {
			// We need typeUtils for erasure, but we don't have it here.
			// Use the raw type string as a fallback — good enough for dedup.
			sb.append(p.asType().toString()).append(",");
		}
		sb.append(")");
		return sb.toString();
	}

	private static TypeElement asTypeElement(TypeMirror typeMirror, ProcessingEnvironment processingEnv) {
		if (processingEnv == null)
			return null;
		javax.lang.model.element.Element element = processingEnv.getTypeUtils().asElement(typeMirror);
		return (element instanceof TypeElement te) ? te : null;
	}

	private static class ProxyMethod {
		ExecutableElement method;
		TypeElement declaringInterface;
	}
}
