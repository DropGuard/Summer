package com.github.dropguard.summer.tck.invisible.fixtures.narrow.dup.B;

import com.github.dropguard.summer.core.Component;

/**
 * Same simple name as {@code narrow.dup.A.Thing} — legitimate user code across packages. The AOT
 * engine's generated wire declares one variable per bean, so without variable-name dedup the
 * generated code would fail to compile; this fixture pair exists to prove it compiles and runs.
 */
@Component
public class Thing {}
