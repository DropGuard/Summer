package com.github.dropguard.summer.core.util;

import com.github.dropguard.summer.core.Internal;
import java.util.regex.Pattern;

/**
 * The framework's one and only place for {@link Pattern} constants. Concentrating regex literals
 * here kills the "escape hell" that comes from each call site duplicating {@code "\\\\s"} versus
 * {@code "\\s"} versus {@code "\s"}: reviewers see the regex intent (named constant), Java's
 * string-literal layer is centralised, and {@code Pattern.CASE_INSENSITIVE} or other flags can be
 * applied uniformly.
 *
 * <p>Convention: every pattern constant carries a one-line javadoc stating <em>what</em> it matches
 * and (where non-obvious) <em>why</em> the regex flavour was chosen over substring tests.
 *
 * <p>Call sites MUST go through these constants — direct {@code String.matches("...")} or {@code
 * Pattern.compile("...")} calls in main code are caught by {@code RegexLiteralDisciplineTest}
 * (summer-archunit). The rule exists because the audit caught a real bug: {@code
 * "summer:\s*(#.*)?"} in a Java string literal is a literal {@code s}, not the regex meta-char
 * {@code \s}, and the only way to keep that mistake from coming back is to forbid the inline form.
 */
@Internal
public final class Regexes {

    private Regexes() {}

    /**
     * Matches a {@code summer:} block header, optionally followed by whitespace and an inline
     * comment — used to detect the start of the summer YAML block before its first key.
     */
    public static final Pattern SUMMER_BLOCK_HEADER = Pattern.compile("summer:\\s*(#.*)?");

    /**
     * Matches an existing {@code engine:} key line indented with one or more whitespace chars
     * (space or tab); the value and any trailing inline comment are captured. Indentation
     * deliberately allows tab-indented YAML — string equality on tab was the silent bug this
     * constant was extracted from.
     */
    public static final Pattern ENGINE_LINE = Pattern.compile("\\s+engine:\\s*.*");

    /**
     * Matches a {@code ${var}} placeholder with optional {@code :-default} suffix and captures the
     * variable name, the default-present flag and the default value separately. Used by {@code
     * ConfigBinder} for externalised config substitution.
     */
    public static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{([\\w.]+)(?::(-?))?([^}]*)\\}");

    /**
     * Matches an Express-style {@code :name} route parameter placeholder — a colon directly
     * preceded by a path boundary ({@code /} or start-of-path). Used by {@code HttpRouter.Builder}
     * to normalise {@code /books/:id} into the canonical {@code /books/{id}} syntax that every
     * router implementation understands.
     */
    public static final Pattern COLON_PARAM = Pattern.compile("(?<=/|^):([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * Convenience wrapper around {@link String#matches(String)} using a constant pattern. The call
     * is equivalent to {@code Pattern.matcher(input).matches()} but reuses the compiled {@link
     * Pattern} — no per-call recompile, and reviewers see the named constant at the call site.
     */
    public static boolean matches(CharSequence input, Pattern pattern) {
        return pattern.matcher(input).matches();
    }
}
