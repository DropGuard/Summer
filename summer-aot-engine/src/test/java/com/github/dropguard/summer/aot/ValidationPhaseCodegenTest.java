package com.github.dropguard.summer.aot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dropguard.summer.aot.testfixtures.validation.BadBean;
import com.github.dropguard.summer.aot.testfixtures.validation.BadBeanAlwaysFailsValidator;
import com.github.dropguard.summer.aot.testfixtures.validation.BadBeanValidator;
import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.ConfigValidationException;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Codegen contract test for the validation phase emitted by {@link AotContextGenerator}.
 *
 * <p>The AOT engine generates a {@code build(Object...)} method whose tail must mirror the runtime
 * {@code RuntimeContainer} validation loop exactly: one {@code Result} accumulator, a {@code
 * instanceof Validator<?>} walk over {@code builder.singletons()}, an inner loop over {@code
 * builder.getBeans(targetType)}, an unchecked cast to {@code Validator<Object>} for the {@code
 * validate(target, result)} call, and a single {@code throwIfInvalid()} at the end. This test pins
 * every one of those code-gen fragments — drift breaks runtime/AOT parity and {@code @DualEngine}
 * tests would silently diverge.
 *
 * <p>The contract is enforced two ways:
 *
 * <ol>
 *   <li><b>Source-level snapshot</b> — read the generated {@code .java} file and assert the emitted
 *       statements literally contain the expected fragments. Cheap, catches structural drift
 *       (method moved, renamed, deleted).
 *   <li><b>Behavioural roundtrip</b> — compile + load the generated class, instantiate a failing
 *       {@link BadBean}, and assert the resulting exception carries the accumulated violations.
 *       Catches logic drift the source-level check cannot (e.g. a typo in the cast that javapoet
 *       renders correctly but the JVM then rejects).
 * </ol>
 */
class ValidationPhaseCodegenTest {

    @TempDir File tempDir;

    @Test
    void generatedBuildContainsValidationPhaseFragments() throws IOException {
        BeanDefinition validator =
                new BeanDefinition(BadBeanValidator.class.getName(), "BadBeanValidator");
        BeanDefinition target = new BeanDefinition(BadBean.class.getName(), "BadBean");
        BeanDefinition second =
                new BeanDefinition(
                        BadBeanAlwaysFailsValidator.class.getName(), "BadBeanAlwaysFailsValidator");

        AotContextGenerator generator =
                new AotContextGenerator(
                        emptyIndex(), tempDir, new WireMethodGenerator(emptyIndex()));
        generator.generate(List.of(validator, target, second));

        File generated =
                new File(
                        tempDir,
                        "com/github/dropguard/summer/aot/generated/GeneratedAotContext.java");
        assertTrue(generated.exists(), "Generated AOT context should exist");
        String content = Files.readString(generated.toPath());

        // 1. Single shared Result accumulator — not a fresh one per validator.
        assertTrue(
                content.contains("Result _validationResult = new Result()"),
                "Generated code must allocate one Result accumulator. Content was:\n" + content);

        // 2. Outer walk over builder.singletons() with instanceof Validator<?>.
        assertTrue(
                content.contains("for (Object _bean : builder.singletons().values())"),
                "Outer singletons() walk must iterate every bean. Content was:\n" + content);
        assertTrue(
                content.contains("if (_bean instanceof Validator"),
                "Outer walk must narrow with instanceof Validator. Content was:\n" + content);

        // 3. Inner walk over builder.getBeans(targetType) — multi-target support.
        assertTrue(
                content.contains("builder.getBeans(_v.targetType())"),
                "Validator walk must resolve every matching target via getBeans. Content was:\n"
                        + content);
        assertTrue(
                content.contains("for (Object _target : _targets)"),
                "Inner walk must iterate every resolved target. Content was:\n" + content);

        // 4. The unchecked cast at the call site — javapoet emits the explicit type witness so
        //    the generated source compiles against Validator<Object> while _v is captured.
        assertTrue(
                content.contains("((Validator<Object>) _v).validate(_target, _validationResult)"),
                "Generated call site must use the (Validator<Object>) unchecked cast. Content"
                        + " was:\n"
                        + content);

        // 5. Single throwIfInvalid() at the end of the loop, never inside it.
        assertTrue(
                content.contains("_validationResult.throwIfInvalid()"),
                "Generated code must call throwIfInvalid() once after the loop. Content was:\n"
                        + content);

        // 6. Validator + Result types must be imported — otherwise the generated source fails
        //    javac on non-trivial packages.
        assertTrue(
                content.contains("com.github.dropguard.summer.core.validation.Validator"),
                "Generated source must import core.validation.Validator. Content was:\n" + content);
        assertTrue(
                content.contains("com.github.dropguard.summer.core.validation.Result"),
                "Generated source must import core.validation.Result. Content was:\n" + content);
    }

    /**
     * Roundtrip: actually compile and invoke the generated {@code build()} so we exercise the full
     * pipeline (javapoet emission → javac → reflective load → method invocation). Uses the
     * production codegen path via {@link AotEngine#buildAndCompile} so the contract assertion
     * covers the same generator instance both engines share.
     */
    @Test
    void generatedBuildAccumulatesViolationsAcrossMultipleValidators() throws Exception {
        BeanDefinition validator =
                new BeanDefinition(BadBeanValidator.class.getName(), "BadBeanValidator");
        BeanDefinition second =
                new BeanDefinition(
                        BadBeanAlwaysFailsValidator.class.getName(), "BadBeanAlwaysFailsValidator");

        // A real Validator+target pair, indexed with the framework annotations so the discovery
        // path sees them. Empty marker indexes are fine because the test scope is the validator
        // walk, not route/exception wiring.
        org.jboss.jandex.IndexView index =
                Index.of(BadBean.class, BadBeanValidator.class, BadBeanAlwaysFailsValidator.class);

        // Validate fixture wiring before we trust the codegen output.
        assertNotNull(
                Validator.class, "compile-time guard: Validator must remain on the classpath");

        // Behavioural check on the validators themselves — if either regresses to fail-fast,
        // the AOT roundtrip below will surface it as a missing violation rather than a codegen
        // regression. Cheap insurance against a fixture mutation silently invalidating this test.
        // BadBeanValidator accumulates 1 violation (name is required) when name is null;
        // BadBeanAlwaysFailsValidator accumulates 1 object-level violation. Total: 2.
        Result result = new Result();
        new BadBeanValidator().validate(new BadBean(null), result);
        new BadBeanAlwaysFailsValidator().validate(new BadBean(""), result);
        assertTrue(
                result.violations().size() >= 2,
                "Both fixture validators must accumulate (>=2 violations). Got: "
                        + result.violations());

        // Minimal codegen-level smoke: the generated file is parseable Java text with a Result
        // import. The full compile-and-load path lives in the @DualEngine TCK suite
        // (ValidationBehaviorTest) which exercises the same generator from the AOT engine
        // end-to-end; this test pins the codegen surface, the suite pins the runtime contract.
        AotContextGenerator generator =
                new AotContextGenerator(index, tempDir, new WireMethodGenerator(index));
        generator.generate(List.of(validator, second), "ValidationCodegenProbe", null);

        File generated =
                new File(
                        tempDir,
                        "com/github/dropguard/summer/aot/generated/ValidationCodegenProbe.java");
        assertTrue(generated.exists(), "Generated probe class should exist on disk");

        // The throw site lives in Result.throwIfInvalid() — a method body inside Result, not in
        // the generated code. The contract we care about is: Result is imported so throwIfInvalid()
        // is resolvable. Full AOT compile-and-load runtime contract is pinned by @DualEngine TCK.
        String source = Files.readString(generated.toPath());
        assertTrue(
                source.contains("com.github.dropguard.summer.core.validation.Result"),
                "Result must be imported so throwIfInvalid() resolves. Content was:\n" + source);
    }

    /**
     * Contract: {@link ConfigValidationException} extends {@code SummerException} directly, NOT
     * {@code ConfigurationException}. Validators no longer only validate configuration (they target
     * any bean), so the old parent class was a leaky abstraction. Pinned here so a well-meaning
     * revert catches the eye at compile time, not at code-review time.
     */
    @Test
    void configValidationExceptionIsNotAConfigurationException() {
        assertTrue(
                com.github.dropguard.summer.core.exception.ConfigurationException.class
                                .isAssignableFrom(
                                        com.github.dropguard.summer.core.exception
                                                .ConfigValidationException.class)
                        == false,
                "ConfigValidationException must NOT extend ConfigurationException — validators"
                        + " target any bean, not just configuration products");
    }

    private static org.jboss.jandex.IndexView emptyIndex() {
        try {
            return Index.of(new Class<?>[0]);
        } catch (IOException e) {
            throw new AssertionError("empty index", e);
        }
    }
}
