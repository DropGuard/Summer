package summer.core.bean;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import summer.core.exception.AmbiguousBeanException;
import summer.core.exception.NoSuchBeanException;

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

		NoSuchBeanException ex = assertThrows(NoSuchBeanException.class, () -> evaluator.evaluate(beans));
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

		AmbiguousBeanException ex = assertThrows(AmbiguousBeanException.class, () -> evaluator.evaluate(beans));
		assertTrue(ex.getMessage().contains("multiple @Bean methods return com.TargetBean"));
	}
}
