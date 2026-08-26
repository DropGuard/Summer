package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.*;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link AotTypeNames} edge behaviour: default-package names and arrays used to crash or
 * silently degrade generated code.
 */
class AotTypeNamesTest {

    @Test
    void defaultPackageClassNameDoesNotUnderflow() {
        // substring(0, lastDot) used to become substring(0, -1) and throw.
        assertDoesNotThrow(() -> AotTypeNames.parseTypeName("PlainFoo"));
        assertEquals("PlainFoo", AotTypeNames.parseTypeName("PlainFoo").toString());
    }

    @Test
    void arrayTypeNamesFailLoudlyInsteadOfBecomingObject() {
        var ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AotTypeNames.parseTypeName("[Lcom.app.Thing;"));
        assertTrue(ex.getMessage().contains("[Lcom.app.Thing;"));
    }

    @Test
    void nestedClassDollarFormStillResolves() {
        var parsed = AotTypeNames.parseTypeName("com.app.web.Config$RouterType");
        assertEquals("com.app.web.Config.RouterType", parsed.toString());
    }

    // --- metaFieldName: overloads must produce distinct metadata fields ---

    interface OverloadFixture {
        String find(String key);

        String find(int index);
    }

    @Test
    void overloadedBoundMethodsGetDistinctMetaFields() {
        try {
            Index index = Index.of(OverloadFixture.class);
            ClassInfo ci =
                    index.getClassByName(DotName.createSimple(OverloadFixture.class.getName()));
            String byString =
                    AotProxyGenerator.metaFieldName(methodNamed(ci, "find", "java.lang.String"));
            String byInt = AotProxyGenerator.metaFieldName(methodNamed(ci, "find", "int"));

            assertNotEquals(
                    byString,
                    byInt,
                    "two bound overloads must not emit colliding META_ fields — "
                            + "the generated source would not compile");
            assertTrue(byString.startsWith("META_find"));
            assertTrue(byInt.startsWith("META_find"));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static MethodInfo methodNamed(ClassInfo ci, String name, String paramTypeName) {
        return ci.methods().stream()
                .filter(m -> m.name().equals(name))
                .filter(m -> m.parametersCount() == 1)
                .filter(m -> m.parameterTypes().get(0).name().toString().equals(paramTypeName))
                .findFirst()
                .orElseThrow();
    }
}
