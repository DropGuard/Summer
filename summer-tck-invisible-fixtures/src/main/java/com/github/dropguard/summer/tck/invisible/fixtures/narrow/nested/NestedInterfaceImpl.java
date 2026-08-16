package com.github.dropguard.summer.tck.invisible.fixtures.narrow.nested;

import com.github.dropguard.summer.core.Component;

/**
 * A bean implementing a NESTED interface. The AOT engine registers beans under their implemented
 * interfaces, so the generated code must reference {@code NestedHolder.Router} — a broken import
 * (importing the nested type by its dotted binary name) fails the generated compile.
 */
@Component
public class NestedInterfaceImpl implements NestedHolder.Router {}
