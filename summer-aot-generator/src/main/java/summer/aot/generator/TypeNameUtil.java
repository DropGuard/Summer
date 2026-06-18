package summer.aot.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

/**
 * Shared utility for converting type name strings to JavaPoet TypeName.
 */
public final class TypeNameUtil {

	static TypeName fromString(String name) {
		return switch (name) {
			case "void" -> TypeName.VOID;
			case "int" -> TypeName.INT;
			case "long" -> TypeName.LONG;
			case "double" -> TypeName.DOUBLE;
			case "boolean" -> TypeName.BOOLEAN;
			case "float" -> TypeName.FLOAT;
			case "byte" -> TypeName.BYTE;
			case "short" -> TypeName.SHORT;
			case "char" -> TypeName.CHAR;
			default -> classNameFromString(name);
		};
	}

	/**
	 * Convert a fully qualified class name to a ClassName, properly handling
	 * inner classes denoted by {@code $}.
	 */
	static ClassName classNameFromString(String name) {
		int dollarIdx = name.indexOf('$');
		if (dollarIdx < 0) {
			return ClassName.bestGuess(name);
		}
		String outerName = name.substring(0, dollarIdx);
		String innerName = name.substring(dollarIdx + 1);
		ClassName outer = ClassName.bestGuess(outerName);
		return outer.nestedClass(innerName);
	}

	static String packageName(String qualifiedName) {
		int lastDot = qualifiedName.lastIndexOf('.');
		return lastDot > 0 ? qualifiedName.substring(0, lastDot) : "";
	}

	private TypeNameUtil() {
	}
}
