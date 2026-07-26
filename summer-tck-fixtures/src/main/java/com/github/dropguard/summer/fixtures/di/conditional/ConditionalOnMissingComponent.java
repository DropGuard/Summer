package com.github.dropguard.summer.fixtures.di.conditional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

@Component
@ConditionalOnBean(MissingComponent.class)
public class ConditionalOnMissingComponent {
}
