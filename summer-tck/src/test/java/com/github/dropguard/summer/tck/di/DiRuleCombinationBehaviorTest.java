package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.core.Engine;
import com.github.dropguard.summer.core.bean.MockedBean;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboAbsent;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboBaseGreeter;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboConditionalReplacerConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboCycleA;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboCycleB;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboDefaultServiceConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboFeature;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboGatedConsumer;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboGreeter;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboLegacyGreeter;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboModernGreeter;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboProps;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboPropsConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboPropsConsumer;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboPropsReplacementConfig;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboService;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.ComboUpgradedGreeter;
import com.github.dropguard.summer.tck.invisible.fixtures.combo.FeatureGated;
import com.github.dropguard.summer.test.TestContainer;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * End-to-end contracts for how the DI selection rules compose — {@code @Mock} removal,
 * {@code @ConditionalOnBean}, {@code @Replaces} and the {@code @ConfigMapping} override — from real
 * annotations through discovery and SharedConditionEvaluator to the built container, per engine.
 * Each rule has its own single-rule tck test; these pin the interactions between them.
 */
class DiRuleCombinationBehaviorTest {

    private static TestContainer.Builder universe(Engine engine, Class<?>... beans) {
        return TestContainer.builder()
                .testClass(DiRuleCombinationBehaviorTest.class)
                .engine(engine)
                .beans(beans);
    }

    private static List<MockedBean> mockOf(Class<?> type) {
        return List.of(MockedBean.of(type, mock(type)));
    }

    /** A: a mocked bean still satisfies conditions on its type — the mock is in the container. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void mockedTypeSatisfiesConditionalOnBean(Engine engine) {
        BeanContainer context =
                universe(engine, ComboFeature.class, FeatureGated.class)
                        .mocks(mockOf(ComboFeature.class))
                        .build();
        assertEquals(1, context.getBeans(FeatureGated.class).size(), engine + ": gated bean");
    }

    /** B: a method-level replacer whose condition fails replaces nothing. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void conditionedOutMethodReplacerLeavesTargetInPlace(Engine engine) {
        BeanContainer context =
                universe(
                                engine,
                                ComboDefaultServiceConfig.class,
                                ComboConditionalReplacerConfig.class)
                        .build();
        assertEquals("default", context.getBean(ComboService.class).name(), engine.toString());
    }

    /** C: replacing a target that conditions already removed is not a missing-target error. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void replacingAConditionedOutTargetIsNotAnError(Engine engine) {
        BeanContainer context =
                universe(engine, ComboLegacyGreeter.class, ComboModernGreeter.class).build();
        assertEquals("modern", context.getBean(ComboGreeter.class).greet(), engine.toString());
    }

    /** D: a producer orphaned by its replaced @Configuration no longer overrides the mapping. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void producerOfAReplacedConfigurationDoesNotOverrideMapping(Engine engine) {
        BeanContainer context =
                universe(
                                engine,
                                ComboProps.class,
                                ComboPropsConfig.class,
                                ComboPropsReplacementConfig.class)
                        .build();
        assertEquals("from-mapping", context.getBean(ComboProps.class).value(), engine.toString());
    }

    /** E: a @Mock of a mapping type replaces both the mapping and its producer override. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void mockOfMappingTypeWinsOverMappingAndProducer(Engine engine) {
        List<MockedBean> mocks = mockOf(ComboProps.class);
        BeanContainer context =
                universe(engine, ComboProps.class, ComboPropsConfig.class).mocks(mocks).build();
        assertSame(mocks.get(0).instance(), context.getBean(ComboProps.class), engine.toString());
    }

    /** F: a @ConfigMapping bean satisfies a condition on its type. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void configMappingSatisfiesConditionalOnBean(Engine engine) {
        BeanContainer context =
                universe(engine, ComboProps.class, ComboPropsConsumer.class).build();
        assertEquals(1, context.getBeans(ComboPropsConsumer.class).size(), engine.toString());
    }

    /**
     * G: mocking a class-level @Replaces target displaces its replacer — the mock takes over the
     * position the replacement chain holds in production, so only the mock remains.
     */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void mockOfAReplacedTargetDisplacesItsReplacer(Engine engine) {
        List<MockedBean> mocks = mockOf(ComboBaseGreeter.class);
        BeanContainer context =
                universe(engine, ComboBaseGreeter.class, ComboUpgradedGreeter.class)
                        .mocks(mocks)
                        .build();
        assertSame(mocks.get(0).instance(), context.getBean(ComboGreeter.class), engine + "");
        assertEquals(0, context.getBeans(ComboUpgradedGreeter.class).size(), engine + ": replacer");
    }

    /**
     * I: a missing-bean failure explains itself — when the missing type was discovered but
     * withdrawn, the error names the withdrawn candidate and the reason.
     */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void missingBeanErrorNamesTheWithdrawal(Engine engine) {
        Throwable thrown =
                assertThrows(
                        Throwable.class,
                        () ->
                                universe(engine, FeatureGated.class, ComboGatedConsumer.class)
                                        .build());
        NoSuchBeanException missing = null;
        for (Throwable t = thrown; t != null && missing == null; t = t.getCause()) {
            if (t instanceof NoSuchBeanException e) missing = e;
        }
        assertNotNull(missing, engine + ": expected a NoSuchBeanException, got " + thrown);
        assertEquals(FeatureGated.class.getName(), missing.missingType, engine.toString());
        assertEquals(1, missing.withdrawn.size(), engine + ": " + missing.getMessage());
        String reason = missing.withdrawn.get(0);
        assertTrue(
                reason.contains("@ConditionalOnBean(" + ComboFeature.class.getName() + ")"),
                engine + ": " + reason);
        assertTrue(
                missing.getMessage().contains("withdrawn during assembly"),
                engine + ": " + missing.getMessage());
    }

    /** H: mocking the replacer leaves its target displaced, exactly as in production. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void mockOfAReplacerKeepsItsTargetDisplaced(Engine engine) {
        List<MockedBean> mocks = mockOf(ComboModernGreeter.class);
        BeanContainer context =
                universe(engine, ComboLegacyGreeter.class, ComboModernGreeter.class)
                        .mocks(mocks)
                        .build();
        assertSame(mocks.get(0).instance(), context.getBean(ComboGreeter.class), engine + "");
    }

    /**
     * J: a mock cannot stand in for a bean production withdraws. ComboLegacyGreeter is conditioned
     * out (ComboModernGreeter stands on its own), so mocking it fails the build with the reason.
     */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void mockOfAConditionedOutTypeFails(Engine engine) {
        Throwable thrown =
                assertThrows(
                        Throwable.class,
                        () ->
                                universe(engine, ComboLegacyGreeter.class, ComboModernGreeter.class)
                                        .mocks(mockOf(ComboLegacyGreeter.class))
                                        .build());
        BeanCreationException failure = causeOf(thrown, BeanCreationException.class);
        assertNotNull(failure, engine + ": expected a BeanCreationException, got " + thrown);
        assertTrue(
                failure.getMessage().contains("stands in for nothing"),
                engine + ": " + failure.getMessage());
        assertTrue(
                failure.getMessage().contains(ComboAbsent.class.getName()),
                engine + ": " + failure.getMessage());
    }

    /** K: a conditional cycle fails as in production; mocking one side does not hide it. */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void conditionalCycleIsNotHiddenByAMock(Engine engine) {
        Throwable thrown =
                assertThrows(
                        Throwable.class,
                        () ->
                                universe(engine, ComboCycleA.class, ComboCycleB.class)
                                        .mocks(mockOf(ComboCycleB.class))
                                        .build());
        BeanCreationException failure = causeOf(thrown, BeanCreationException.class);
        assertNotNull(failure, engine + ": expected a BeanCreationException, got " + thrown);
        assertTrue(
                failure.getMessage().contains("@ConditionalOnBean cycle"),
                engine + ": " + failure.getMessage());
    }

    private static <T extends Throwable> T causeOf(Throwable thrown, Class<T> type) {
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return type.cast(t);
        }
        return null;
    }
}
