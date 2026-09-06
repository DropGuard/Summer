package com.github.dropguard.summer.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.bean.BeanDefinition;
import com.github.dropguard.summer.core.exception.AmbiguousBeanException;
import com.github.dropguard.summer.core.exception.NoSuchBeanException;
import java.util.ArrayList;
import java.util.List;
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
}
