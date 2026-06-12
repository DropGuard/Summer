package summer.fixtures.di.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

@Component
@ConditionalOnBean(MissingComponent.class)
public class ConditionalOnMissingComponent {
}
