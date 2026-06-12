package summer.fixtures.di.conditional;

import summer.core.Component;
import summer.core.annotation.ConditionalOnBean;

@Component
@ConditionalOnBean(RequiredComponent.class)
public class ConditionalOnComponent {
}
