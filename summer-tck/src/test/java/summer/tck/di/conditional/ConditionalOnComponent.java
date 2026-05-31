package summer.tck.di.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

/**
 * Component that should be registered only when RequiredComponent exists.
 */
@Component
@ConditionalOnBean(RequiredComponent.class)
public class ConditionalOnComponent {
}
