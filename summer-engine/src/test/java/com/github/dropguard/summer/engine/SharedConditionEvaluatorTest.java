package com.github.dropguard.summer.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.bean.ConfigPropertiesBean;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
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
    void mockSuppliedInterfaceKeySurvivesADroppedRealImplementor() {
        // RealCodec is mocked (so removed before contributors are counted); XmlCodec is the only
        // remaining real Codec and drops on its own condition. Without the mock's permanent keys,
        // that drop would revoke Codec and cascade a false drop onto Encoder.
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
        assertTrue(beans.contains(encoder), "the mock still supplies Codec");
    }

    /** The owning @Configuration: a product whose configuration is absent is purged as orphaned. */
    private static BeanDefinition configClass(String name) {
        return new BeanDefinition(name, name.substring(name.lastIndexOf('.') + 1));
    }
}
