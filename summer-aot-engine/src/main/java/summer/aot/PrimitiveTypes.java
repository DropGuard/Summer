package summer.aot;

/**
 * AOT-internal source of truth for primitive type names: the raw {@link Class}
 * for a primitive's simple name ({@code "int"} &rarr; {@code int.class}) and a
 * primitive-or-not predicate. Both queries draw on one enumeration of primitive
 * names, so the set lives in exactly one place.
 *
 * <p>
 * Codegen needs the raw {@link TypeName} for a primitive declaration
 * ({@code int}, not {@code java.lang.Integer}); this table is the one place
 * that names the raw type. Boxed forms ({@code "java.lang.Integer"}) are
 * deliberately out of scope: codegen resolves them as ordinary reference types,
 * and the JDBC mapper resolves every numeric type to its boxed {@link Class} —
 * different dimensions, not the same mapping, so they are not folded in here.
 * This lives in the AOT engine because it serves only AOT code generation.
 */
public final class PrimitiveTypes {

	private PrimitiveTypes() {
	}

	/**
	 * Returns the raw {@link Class} for a primitive type's simple name, or
	 * {@code null} if the name is not a primitive (the caller then applies its own
	 * fallback — e.g. treat it as a reference type).
	 */
	public static Class<?> rawType(String typeName) {
		return switch (typeName) {
			case "int" -> int.class;
			case "long" -> long.class;
			case "double" -> double.class;
			case "float" -> float.class;
			case "boolean" -> boolean.class;
			case "short" -> short.class;
			case "byte" -> byte.class;
			case "char" -> char.class;
			default -> null;
		};
	}

	/**
	 * Whether the given type name denotes a primitive type. Reuses
	 * {@link #rawType}'s single enumeration of primitive names, so the primitive
	 * set lives in exactly one place.
	 */
	public static boolean isPrimitive(String typeName) {
		return rawType(typeName) != null;
	}
}
