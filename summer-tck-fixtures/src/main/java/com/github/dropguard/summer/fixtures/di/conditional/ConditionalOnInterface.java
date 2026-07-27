package com.github.dropguard.summer.fixtures.di.conditional;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

@Component
@ConditionalOnBean(RequiredInterface.class)
public class ConditionalOnInterface {}
