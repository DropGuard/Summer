package com.github.dropguard.summer.aot.testfixtures;

import com.github.dropguard.summer.core.config.ConfigMapping;

/**
 * Fixture exercising numeric config binding. The AOT engine must coerce resolved section values
 * (which arrive as {@link Number}s, e.g. an {@code Integer} from YAML) into the declared boxed
 * target rather than emitting a bare primitive cast — see {@code WireMethodGenerator.coerceExpr}
 * and the regression it guards.
 */
@ConfigMapping(prefix = "numeric")
public interface NumericConfig {

    long expirationMs();

    int port();

    double ratio();

    String name();
}
