package com.github.dropguard.summer.engine.testfixtures.pipeline;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** A bean conditioned on a non-bean class — condition evaluation must drop it. */
@Component
@ConditionalOnBean(NotABean.class)
public class ConditionalController {}
