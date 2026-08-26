package com.github.dropguard.summer.aot;

import com.github.dropguard.summer.core.Internal;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

/**
 * Type-name helpers shared by the AOT generators.
 *
 * <p>Generators operate on Jandex-derived type names, which use JVM-internal {@code $} for nested
 * classes and raw names for primitives. These helpers normalize them into JavaPoet types.
 */
@Internal
public final class AotTypeNames {

    private AotTypeNames() {}

    /**
     * Converts a JVM type name to a JavaPoet {@link TypeName}. The primitive mapping is delegated
     * to {@link TypeReads#typeName(String)}; this method adds array / nested-class ({@code $})
     * preprocessing that only applies to Jandex-derived type names.
     */
    public static TypeName parseTypeName(String typeName) {
        if (typeName.startsWith("[")) {
            // Arrays were silently mapped to Object.class, which produced generated code that
            // compiled but behaved wrongly. Array-typed bean/parameter shapes are unsupported —
            // say so at generation time instead.
            throw new IllegalArgumentException(
                    "Array types are not supported by AOT generation: " + typeName);
        }
        if (PrimitiveTypes.isPrimitive(typeName)) return TypeReads.typeName(typeName);
        // Jandex rawType names use JVM internal '$' for nested classes (e.g.
        // WebConfig$RouterType). Render the source form WebConfig.RouterType via
        // ClassName's nested-class constructor so the generated import resolves.
        String dotted = typeName.replace('$', '.');
        String[] parts = dotted.split("\\.");
        if (parts.length == 1) {
            // Default package: no dot to split on (substring(0, -1) used to throw here).
            return ClassName.get("", parts[0]);
        }
        String pkg = dotted.substring(0, dotted.lastIndexOf('.'));
        String[] nested = dotted.substring(dotted.lastIndexOf('.') + 1).split("\\.");
        if (nested.length == 1) {
            return ClassName.get(pkg, nested[0]);
        }
        return ClassName.get(
                pkg, nested[0], java.util.Arrays.copyOfRange(nested, 1, nested.length));
    }

    /**
     * Creates a {@link ClassName} from a qualified name that may contain JVM-internal {@code $}
     * nested-class separators.
     *
     * <p>The split is explicit, never {@link ClassName#bestGuess}: bestGuess infers the package
     * boundary from the last all-lowercase segment, which misreads a package segment that starts
     * uppercase as a nested class (e.g. {@code com.x.dup.A.Thing} — package {@code com.x.dup.A},
     * class {@code Thing} — becomes nested {@code A.Thing} and the generated import fails). The
     * {@code $} separator is the only reliable nested-class marker, so it is split on first.
     */
    public static ClassName safeClassName(String qualifiedName) {
        String enclosing = qualifiedName;
        String[] nested = new String[0];
        int dollar = qualifiedName.indexOf('$');
        if (dollar != -1) {
            enclosing = qualifiedName.substring(0, dollar);
            nested = qualifiedName.substring(dollar + 1).split("\\$");
        }
        int lastDot = enclosing.lastIndexOf('.');
        if (lastDot == -1) {
            return nested.length == 0
                    ? ClassName.get("", enclosing)
                    : ClassName.get("", enclosing, nested);
        }
        String pkg = enclosing.substring(0, lastDot);
        String topLevel = enclosing.substring(lastDot + 1);
        return nested.length == 0
                ? ClassName.get(pkg, topLevel)
                : ClassName.get(pkg, topLevel, nested);
    }
}
