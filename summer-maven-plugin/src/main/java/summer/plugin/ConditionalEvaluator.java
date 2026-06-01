package summer.plugin;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Evaluates @ConditionalOnBean conditions and removes beans whose
 * conditions are not satisfied.
 * 
 * <p>This is the Maven plugin equivalent of the old ConditionalEvaluator
 * that used ProcessingEnvironment. This version uses Jandex Index instead.</p>
 */
public final class ConditionalEvaluator {

	private ConditionalEvaluator() {
	}

	/**
	 * Evaluate @ConditionalOnBean conditions and remove unsatisfied beans.
	 * 
	 * @param beans list of bean definitions (will be modified)
	 * @param index Jandex index for looking up classes
	 */
	public static void evaluate(List<BeanDefinition> beans, IndexView index) {
		DotName conditionalDot = DotName.createSimple("summer.core.annotation.ConditionalOnBean");

		boolean changed = true;
		while (changed) {
			changed = false;
			List<BeanDefinition> toRemove = new ArrayList<>();

			for (BeanDefinition bean : beans) {
				ClassInfo ci = index.getClassByName(DotName.createSimple(bean.qualifiedName));
				if (ci == null) continue;

				AnnotationInstance condAnn = ci.annotation(conditionalDot);
				if (condAnn == null) continue;

				// Get the required bean type from the annotation
				String requiredType = condAnn.value().asClass().name().toString();

				// Check if the required bean exists
				boolean satisfied = false;
				for (BeanDefinition other : beans) {
					if (other == bean) continue;
					if (other.qualifiedName.equals(requiredType) || other.interfaceNames.contains(requiredType)) {
						satisfied = true;
						break;
					}
				}

				if (!satisfied) {
					toRemove.add(bean);
					changed = true;
				}
			}

			beans.removeAll(toRemove);
		}
	}
}
