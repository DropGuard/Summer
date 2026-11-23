package com.github.dropguard.summer.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.BeanCreationException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SharedConditionEvaluatorTest {

    @Test
    void shouldThrowNoSuchBeanExceptionWhenMethodReplacesTargetMissing() throws Exception {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        BeanDefinition replacer = new BeanDefinition("com.NewBean", "newBean");
        replacer.configClassName = "com.Config";
        replacer.producerMethodName = "newBean";
        replacer.methodLevelReplaces = "com.MissingTarget";

        List<BeanDefinition> beans = new ArrayList<>();
        beans.add(replacer);

        NoSuchBeanException ex =
                assertThrows(NoSuchBeanException.class, () -> evaluator.evaluate(beans));
        assertTrue(ex.getMessage().contains("target not found: com.MissingTarget"));
    }

    @Test
    void shouldThrowAmbiguousBeanExceptionWhenMethodReplacesTargetIsAmbiguous() throws Exception {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        BeanDefinition replacer = new BeanDefinition("com.NewBean", "newBean");
        replacer.configClassName = "com.Config";
        replacer.producerMethodName = "newBean";
        replacer.methodLevelReplaces = "com.TargetBean";

        BeanDefinition target1 = new BeanDefinition("com.TargetBean", "target1");
        target1.configClassName = "com.OldConfig1";
        target1.producerMethodName = "oldBean1";

        BeanDefinition target2 = new BeanDefinition("com.TargetBean", "target2");
        target2.configClassName = "com.OldConfig2";
        target2.producerMethodName = "oldBean2";

        List<BeanDefinition> beans = new ArrayList<>();
        beans.add(replacer);
        beans.add(target1);
        beans.add(target2);

        AmbiguousBeanException ex =
                assertThrows(AmbiguousBeanException.class, () -> evaluator.evaluate(beans));
        assertTrue(ex.getMessage().contains("multiple @Bean methods return com.TargetBean"));
    }

    @Test
    void droppingOneImplementorKeepsSharedInterfaceAvailableForSurvivors() {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        // JsonCodec implements Codec unconditionally; XmlCodec requires a FeatureFlag bean that
        // does not exist and is dropped. The old implementation revoked XmlCodec's interface
        // keys unconditionally — stripping Codec from the available set — which cascaded a
        // false drop onto Encoder even though JsonCodec still implements Codec.
        BeanDefinition jsonCodec = new BeanDefinition("com.JsonCodec", "JsonCodec");
        jsonCodec.interfaceNames.add("com.Codec");

        BeanDefinition xmlCodec = new BeanDefinition("com.XmlCodec", "XmlCodec");
        xmlCodec.interfaceNames.add("com.Codec");
        xmlCodec.conditionalOnBeanType = "com.FeatureFlag";

        BeanDefinition encoder = new BeanDefinition("com.Encoder", "Encoder");
        encoder.conditionalOnBeanType = "com.Codec";

        List<BeanDefinition> beans = new ArrayList<>(List.of(jsonCodec, xmlCodec, encoder));
        evaluator.evaluate(beans);

        assertTrue(beans.contains(jsonCodec), "unconditional implementor survives");
        assertFalse(beans.contains(xmlCodec), "implementor without its requirement drops");
        assertTrue(
                beans.contains(encoder),
                "dependent on the SHARED interface must survive another implementor's drop");
    }

    @Test
    void sameTypeProducersAreEvaluatedIndependently() {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        // Two @Bean producers return the same type under different conditions. Keying the
        // requirements by type name merged them, so one producer's condition decided the other's
        // fate; each definition must carry its own requirement and its own contribution.
        BeanDefinition unconditional = new BeanDefinition("com.Codec", "jsonCodec");
        unconditional.configClassName = "com.JsonConfig";
        unconditional.producerMethodName = "jsonCodec";

        BeanDefinition conditional = new BeanDefinition("com.Codec", "xmlCodec");
        conditional.configClassName = "com.XmlConfig";
        conditional.producerMethodName = "xmlCodec";
        conditional.methodConditionalOnBeanType = "com.FeatureFlag";

        BeanDefinition encoder = new BeanDefinition("com.Encoder", "Encoder");
        encoder.conditionalOnBeanType = "com.Codec";

        List<BeanDefinition> beans =
                new ArrayList<>(
                        List.of(
                                conditional,
                                unconditional,
                                encoder,
                                configClass("com.JsonConfig"),
                                configClass("com.XmlConfig")));
        evaluator.evaluate(beans);

        assertTrue(beans.contains(unconditional), "producer without a condition survives");
        assertFalse(beans.contains(conditional), "producer whose requirement is absent drops");
        assertTrue(
                beans.contains(encoder),
                "the surviving producer still satisfies dependents of the shared type");
    }

    @Test
    void droppedProducerDoesNotOverrideConfigMappingDefault() {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        ConfigPropertiesBean mapping = new ConfigPropertiesBean("com.Props", "Props");
        BeanDefinition producer = new BeanDefinition("com.Props", "props");
        producer.configClassName = "com.PropsConfig";
        producer.producerMethodName = "props";
        producer.methodConditionalOnBeanType = "com.Missing";

        List<BeanDefinition> beans =
                new ArrayList<>(List.of(mapping, producer, configClass("com.PropsConfig")));
        evaluator.evaluate(beans);

        assertFalse(beans.contains(producer), "producer whose requirement is absent drops");
        assertTrue(
                beans.contains(mapping),
                "the @ConfigMapping default stays when no producer" + " survives to override it");
    }

    @Test
    void survivingProducerOverridesConfigMappingDefault() {
        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();

        ConfigPropertiesBean direct = new ConfigPropertiesBean("com.Props", "Props");
        BeanDefinition directProducer = new BeanDefinition("com.Props", "props");
        directProducer.configClassName = "com.PropsConfig";
        directProducer.producerMethodName = "props";

        // A producer returning a concrete implementation overrides through its interfaces.
        ConfigPropertiesBean viaInterface = new ConfigPropertiesBean("com.Other", "Other");
        BeanDefinition concreteProducer = new BeanDefinition("com.OtherImpl", "other");
        concreteProducer.configClassName = "com.OtherConfig";
        concreteProducer.producerMethodName = "other";
        concreteProducer.interfaceNames.add("com.Other");

        List<BeanDefinition> beans =
                new ArrayList<>(
                        List.of(
                                direct,
                                directProducer,
                                viaInterface,
                                concreteProducer,
                                configClass("com.PropsConfig"),
                                configClass("com.OtherConfig")));
        evaluator.evaluate(beans);

        assertFalse(beans.contains(direct), "producer of the mapping type overrides the default");
        assertFalse(beans.contains(viaInterface), "implementation producer overrides too");
        assertTrue(beans.contains(directProducer));
        assertTrue(beans.contains(concreteProducer));
    }

    @Test
    void classLevelReplacesOfANeverDiscoveredTargetStillFails() {
        // Withdrawn targets are tolerated; a target that was never a candidate (a typo, a missing
        // module) must keep failing loudly.
        BeanDefinition replacer = new BeanDefinition("com.Modern", "Modern");
        replacer.replacesTargetClass = "com.Typo";

        List<BeanDefinition> beans = new ArrayList<>(List.of(replacer));
        NoSuchBeanException ex =
                assertThrows(
                        NoSuchBeanException.class,
                        () -> new SharedConditionEvaluator().evaluate(beans));
        assertTrue(ex.getMessage().contains("target not found: com.Typo"));
    }

    @Test
    void mockingAClassLevelReplacesTargetDisplacesTheWholeChain() {
        // Production: Newest replaces Modern replaces Legacy. Mocking Legacy takes over that
        // position, so its production occupant yields too — the same outcome as the method-level
        // case, where the replacer shares the mocked return type.
        BeanDefinition legacy = new BeanDefinition("com.Legacy", "Legacy");
        BeanDefinition modern = new BeanDefinition("com.Modern", "Modern");
        modern.replacesTargetClass = "com.Legacy";
        BeanDefinition newest = new BeanDefinition("com.Newest", "Newest");
        newest.replacesTargetClass = "com.Modern";
        BeanDefinition bystander = new BeanDefinition("com.Bystander", "Bystander");

        List<BeanDefinition> beans = new ArrayList<>(List.of(newest, legacy, modern, bystander));
        new SharedConditionEvaluator().evaluate(beans, Set.of("com.Legacy"));

        assertFalse(beans.contains(legacy), "the mock withdrew the real target");
        assertFalse(beans.contains(modern), "the replacer is the occupant the mock displaces");
        assertFalse(beans.contains(newest), "displacement follows the chain transitively");
        assertTrue(beans.contains(bystander), "beans outside the chain are untouched");
    }

    @Test
    void mockingAReplacerAlsoWithdrawsItsTarget() {
        // Production: Modern replaces Legacy, so Legacy never stands. Mocking Modern must not
        // resurrect Legacy next to the mock.
        BeanDefinition legacy = new BeanDefinition("com.Legacy", "Legacy");
        BeanDefinition modern = new BeanDefinition("com.Modern", "Modern");
        modern.replacesTargetClass = "com.Legacy";

        List<BeanDefinition> beans = new ArrayList<>(List.of(legacy, modern));
        new SharedConditionEvaluator().evaluate(beans, Set.of("com.Modern"));

        assertFalse(beans.contains(modern), "the mock withdrew the replacer");
        assertFalse(
                beans.contains(legacy), "the replaced target stays displaced, as in production");
    }

    @Test
    void methodReplacesOfAConditionedOutProducerIsNotAnError() {
        BeanDefinition defaultSvc = new BeanDefinition("com.Svc", "defaultSvc");
        defaultSvc.configClassName = "com.DefaultConfig";
        defaultSvc.producerMethodName = "defaultSvc";
        defaultSvc.methodConditionalOnBeanType = "com.Absent";

        BeanDefinition overrideSvc = new BeanDefinition("com.Svc", "overrideSvc");
        overrideSvc.configClassName = "com.OverrideConfig";
        overrideSvc.producerMethodName = "overrideSvc";
        overrideSvc.methodLevelReplaces = "com.Svc";

        List<BeanDefinition> beans =
                new ArrayList<>(
                        List.of(
                                defaultSvc,
                                overrideSvc,
                                configClass("com.DefaultConfig"),
                                configClass("com.OverrideConfig")));
        new SharedConditionEvaluator().evaluate(beans);

        assertFalse(beans.contains(defaultSvc), "producer without its requirement drops");
        assertTrue(beans.contains(overrideSvc), "the replacer stands");
    }

    @Test
    void mockingAnImplementorLeavesTheLayoutAsInProduction() {
        // The layout is computed without mocks: RealCodec supplies Codec, so Encoder stands even
        // though XmlCodec drops on its own condition. Mocking RealCodec afterwards only swaps the
        // instance in RealCodec's position.
        BeanDefinition realCodec = new BeanDefinition("com.RealCodec", "RealCodec");
        realCodec.interfaceNames.add("com.Codec");

        BeanDefinition xmlCodec = new BeanDefinition("com.XmlCodec", "XmlCodec");
        xmlCodec.interfaceNames.add("com.Codec");
        xmlCodec.conditionalOnBeanType = "com.FeatureFlag";

        BeanDefinition encoder = new BeanDefinition("com.Encoder", "Encoder");
        encoder.conditionalOnBeanType = "com.Codec";

        List<BeanDefinition> beans = new ArrayList<>(List.of(realCodec, xmlCodec, encoder));
        new SharedConditionEvaluator().evaluate(beans, Set.of("com.RealCodec"));

        assertFalse(beans.contains(xmlCodec), "implementor without its requirement drops");
        assertTrue(beans.contains(encoder), "Encoder stands, exactly as in production");
        assertFalse(beans.contains(realCodec), "the mock took RealCodec's position");
    }

    @Test
    void everyWithdrawalIsRecordedWithItsReason() {
        BeanDefinition gated = new BeanDefinition("com.Gated", "Gated");
        gated.interfaceNames.add("com.Port");
        gated.conditionalOnBeanType = "com.Absent";
        BeanDefinition legacy = new BeanDefinition("com.Legacy", "Legacy");
        BeanDefinition modern = new BeanDefinition("com.Modern", "Modern");
        modern.replacesTargetClass = "com.Legacy";
        BeanDefinition mocked = new BeanDefinition("com.Mocked", "Mocked");

        SharedConditionEvaluator evaluator = new SharedConditionEvaluator();
        List<BeanDefinition> beans = new ArrayList<>(List.of(gated, legacy, modern, mocked));
        evaluator.evaluate(beans, Set.of("com.Mocked"));

        assertEquals(List.of(modern), beans);
        assertEquals("replaced by its @Mock", evaluator.withdrawals().get(mocked));
        assertEquals(
                "@ConditionalOnBean(com.Absent) not satisfied", evaluator.withdrawals().get(gated));
        assertEquals("replaced by @Replaces on com.Modern", evaluator.withdrawals().get(legacy));
        assertEquals(
                List.of("com.Gated (@ConditionalOnBean(com.Absent) not satisfied)"),
                evaluator.withdrawalsByType().get("com.Port"),
                "withdrawals are indexed by the interfaces the candidate would have supplied");
    }

    @Test
    void aMockWithoutCounterpartWarnsInAWholeUniverseOnly() {
        BeanDefinition bystander = new BeanDefinition("com.Bystander", "Bystander");

        SharedConditionEvaluator whole = new SharedConditionEvaluator(false);
        whole.evaluate(new ArrayList<>(List.of(bystander)), Set.of("com.Gone"));
        assertEquals(1, whole.mockNotes().size(), whole.mockNotes().toString());
        assertEquals(SharedConditionEvaluator.Severity.WARN, whole.mockNotes().get(0).severity());
        assertTrue(whole.mockNotes().get(0).message().contains("stale mock"));

        SharedConditionEvaluator narrow = new SharedConditionEvaluator(true);
        narrow.evaluate(new ArrayList<>(List.of(bystander)), Set.of("com.Gone"));
        assertEquals(1, narrow.mockNotes().size(), narrow.mockNotes().toString());
        assertEquals(SharedConditionEvaluator.Severity.DEBUG, narrow.mockNotes().get(0).severity());
    }

    @Test
    void mockingATypeProductionWithdrawsFails() {
        // Production: Feature needs Absent and drops; Gated needs Feature and drops with it. A mock
        // of Feature would stand in for nothing and hold Gated up, so the build fails instead.
        BeanDefinition feature = new BeanDefinition("com.Feature", "Feature");
        feature.conditionalOnBeanType = "com.Absent";
        BeanDefinition gated = new BeanDefinition("com.Gated", "Gated");
        gated.conditionalOnBeanType = "com.Feature";

        List<BeanDefinition> beans = new ArrayList<>(List.of(feature, gated));
        BeanCreationException ex =
                assertThrows(
                        BeanCreationException.class,
                        () ->
                                new SharedConditionEvaluator()
                                        .evaluate(beans, Set.of("com.Feature")));
        assertTrue(
                ex.getMessage().startsWith("@Mock com.Feature stands in for nothing"),
                ex.getMessage());
        assertTrue(
                ex.getMessage().contains("@ConditionalOnBean(com.Absent) not satisfied"),
                ex.getMessage());
    }

    @Test
    void mockingAConditionedOutTargetWhoseReplacerStandsFails() {
        // Legacy is conditioned out, so Modern stands on its own, not in Legacy's place. A mock of
        // Legacy has no production position to take.
        BeanDefinition legacy = new BeanDefinition("com.Legacy", "Legacy");
        legacy.conditionalOnBeanType = "com.Absent";
        BeanDefinition modern = new BeanDefinition("com.Modern", "Modern");
        modern.replacesTargetClass = "com.Legacy";

        List<BeanDefinition> beans = new ArrayList<>(List.of(legacy, modern));
        assertThrows(
                BeanCreationException.class,
                () -> new SharedConditionEvaluator().evaluate(beans, Set.of("com.Legacy")));
    }

    @Test
    void aMockThatOnlyReplacesItsOwnBeanIsSilent() {
        BeanDefinition feature = new BeanDefinition("com.Feature", "Feature");
        BeanDefinition gated = new BeanDefinition("com.Gated", "Gated");
        gated.conditionalOnBeanType = "com.Feature";

        SharedConditionEvaluator evaluator = new SharedConditionEvaluator(false);
        evaluator.evaluate(new ArrayList<>(List.of(feature, gated)), Set.of("com.Feature"));
        assertEquals(List.of(), evaluator.mockNotes());
    }

    @Test
    void aMockOfAnUndiscoveredTypeSatisfiesConditions() {
        // Feature is not listed (a narrow test's mocked collaborator): the mock is its only source,
        // so conditions on it pass, with a debug note.
        BeanDefinition gated = new BeanDefinition("com.Gated", "Gated");
        gated.conditionalOnBeanType = "com.Feature";

        SharedConditionEvaluator evaluator = new SharedConditionEvaluator(true);
        List<BeanDefinition> beans = new ArrayList<>(List.of(gated));
        evaluator.evaluate(beans, Set.of("com.Feature"));
        assertEquals(List.of(gated), beans);
        assertEquals(
                List.of(SharedConditionEvaluator.Severity.DEBUG),
                evaluator.mockNotes().stream()
                        .map(SharedConditionEvaluator.MockNote::severity)
                        .toList(),
                evaluator.mockNotes().toString());
    }

    @Test
    void aConditionalCycleFailsEvenWhenOneSideIsMocked() {
        // A needs B and B needs A: production can never satisfy either. A mock of B must not hide
        // that, or the test passes while production fails to start.
        BeanDefinition a = new BeanDefinition("com.A", "A");
        a.conditionalOnBeanType = "com.B";
        BeanDefinition b = new BeanDefinition("com.B", "B");
        b.conditionalOnBeanType = "com.A";

        List<BeanDefinition> beans = new ArrayList<>(List.of(a, b));
        BeanCreationException ex =
                assertThrows(
                        BeanCreationException.class,
                        () -> new SharedConditionEvaluator().evaluate(beans, Set.of("com.B")));
        assertTrue(ex.getMessage().contains("@ConditionalOnBean cycle"), ex.getMessage());
    }

    /** The owning @Configuration: a product whose configuration is absent is purged as orphaned. */
    private static BeanDefinition configClass(String name) {
        return new BeanDefinition(name, name.substring(name.lastIndexOf('.') + 1));
    }
}
