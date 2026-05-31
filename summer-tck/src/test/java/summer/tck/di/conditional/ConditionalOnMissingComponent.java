package summer.tck.di.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

/**
 * Component that should NOT be registered because MissingComponent does not
 * exist.
 */
@Component
@ConditionalOnBean(MissingComponent.class)
public class ConditionalOnMissingComponent {
}
