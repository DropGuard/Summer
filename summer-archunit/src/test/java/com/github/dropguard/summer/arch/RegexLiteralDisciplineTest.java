package com.github.dropguard.summer.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;

/**
 * Regex literals in main code are a concentration hazard: the audit caught a real bug where {@code
 * "summer:\s*(#.*)?"} written as {@code "\s"} in a Java string literal (single escape) compiled
 * into the regex engine as a literal {@code s}, not the meta-char, and the {@code "\\s"} for the
 * engine line silently dropped tab-indented YAML.
 *
 * <p>The discipline that prevents this from coming back is structural, not "be careful":
 *
 * <ul>
 *   <li>Every regex {@link Pattern} constant lives in {@link
 *       com.github.dropguard.summer.core.util.Regexes}.
 *   <li>Call sites use either a named constant from {@code Regexes} or call {@code Regexes.matches}
 *       (whose signature takes the constant {@link Pattern}, never a hand-written {@link String}).
 *   <li>Inline {@code someString.matches(String)} and inline {@code Pattern.compile(String)} in
 *       production code are caught here. Test code is exempt — tests legitimately need ad-hoc
 *       patterns (and test sources are not part of the analyzed imports anyway).
 * </ul>
 *
 * <p>Two deliberate carve-outs keep the rule honest:
 *
 * <ul>
 *   <li>{@code Regexes} itself — it is the one sanctioned home, and its initialisers are the only
 *       place {@code Pattern.compile} legitimately receives a string literal.
 *   <li>{@code PathMatcher.parsePath} — it assembles its regex programmatically (segment-by-segment
 *       from a route pattern) rather than from a hand-written literal; banning it would force the
 *       whole routing translation to live behind a single monolithic constant, which is the wrong
 *       unit of reuse. If a second programmatic assembly site ever appears, review whether the
 *       result should move closer to that builder instead of growing this exemption.
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.github.dropguard.summer",
        importOptions = {ImportOption.DoNotIncludeTests.class, ProductionOnlyImportOption.class})
class RegexLiteralDisciplineTest {

    private static final String REGEXES = "com.github.dropguard.summer.core.util.Regexes";
    private static final String PATH_MATCHER = "com.github.dropguard.summer.web.PathMatcher";

    @ArchTest
    @DisplayName("Inline regex literals are forbidden in production code")
    void noInlineRegexInMainCode(JavaClasses classes) {
        for (JavaClass clazz : classes) {
            if (clazz.getName().equals(REGEXES)) {
                continue;
            }
            for (JavaMethod method : clazz.getMethods()) {
                if (isPathMatcherParsePath(method)) {
                    continue;
                }
                for (JavaMethodCall call : method.getMethodCallsFromSelf()) {
                    if (isInlineRegexCall(call)) {
                        throw new AssertionError(
                                method.getFullName()
                                        + " calls "
                                        + call.getTargetOwner().getSimpleName()
                                        + "."
                                        + call.getName()
                                        + " with an inline regex literal. Move the pattern to a"
                                        + " named Pattern constant in"
                                        + " com.github.dropguard.summer.core.util.Regexes and"
                                        + " invoke it via Regexes.matches(...) or"
                                        + " Regexes.<CONSTANT>.matcher(...).");
                    }
                }
            }
        }
    }

    private static boolean isPathMatcherParsePath(JavaMethod method) {
        return method.getName().equals("parsePath")
                && method.getOwner().getName().equals(PATH_MATCHER);
    }

    /**
     * A call is an inline-regex call when it targets the two regex-literal entry points: {@link
     * String#matches(String)} (whole-string match against an ad-hoc pattern) or {@link
     * Pattern#compile(String)} (ad-hoc compiled pattern). The {@link java.util.regex.Matcher} API
     * (reachable only through a {@code Regexes} constant at that point) is not flagged — {@code
     * Matcher.matches()} is a core part of how {@code Regexes} itself works.
     */
    private static boolean isInlineRegexCall(JavaMethodCall call) {
        JavaClass owner = call.getTargetOwner();
        if (owner.isEquivalentTo(String.class)) {
            return call.getName().equals("matches");
        }
        if (owner.isEquivalentTo(Pattern.class)) {
            return call.getName().equals("compile");
        }
        return false;
    }
}
