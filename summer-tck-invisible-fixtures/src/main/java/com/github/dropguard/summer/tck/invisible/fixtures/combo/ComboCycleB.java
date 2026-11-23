package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** The other side of the {@link ComboCycleA} cycle. */
@Component
@ConditionalOnBean(ComboCycleA.class)
public class ComboCycleB {}
