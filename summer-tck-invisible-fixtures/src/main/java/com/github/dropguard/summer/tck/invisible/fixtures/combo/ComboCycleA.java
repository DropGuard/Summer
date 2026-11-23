package com.github.dropguard.summer.tck.invisible.fixtures.combo;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** One side of a conditional cycle production can never satisfy. */
@Component
@ConditionalOnBean(ComboCycleB.class)
public class ComboCycleA {}
