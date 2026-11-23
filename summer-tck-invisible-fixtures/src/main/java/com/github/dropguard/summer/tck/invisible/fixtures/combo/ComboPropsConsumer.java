package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** Gated on a {@code @ConfigMapping} bean. */
@Component
@ConditionalOnBean(ComboProps.class)
public class ComboPropsConsumer {}
