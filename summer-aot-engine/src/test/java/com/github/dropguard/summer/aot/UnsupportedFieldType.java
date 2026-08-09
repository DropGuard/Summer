package com.github.dropguard.summer.aot;

/**
 * Local rejected-type sample for the codegen contract: a type name that {@code TypeReads} must
 * reject as an unsupported @RowModel field type. Test-local by design (the Quarkus Arc pattern —
 * each narrow test carries its own fixtures; the data-jdbc side has its own sample in {@code
 * summer-data-jdbc/src/test/.../fixtures/}), so no shared fixtures module is needed and the
 * narrow-only fixtures module stays free of a data-jdbc dependency cycle.
 */
public final class UnsupportedFieldType {
    private UnsupportedFieldType() {}
}
