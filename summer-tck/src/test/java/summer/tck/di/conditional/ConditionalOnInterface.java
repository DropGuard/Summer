package summer.tck.di.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

/**
 * Component that should be registered only when RequiredInterface exists.
 * This tests whether @ConditionalOnBean works with interfaces.
 */
@Component
@ConditionalOnBean(RequiredInterface.class)
public class ConditionalOnInterface {
}
