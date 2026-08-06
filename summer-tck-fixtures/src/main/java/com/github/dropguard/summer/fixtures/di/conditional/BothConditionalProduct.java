package com.github.dropguard.summer.fixtures.di.conditional;

import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/** Positive AND-semantics fixture: class-level and method-level prerequisites both exist. */
@ConditionalOnBean(ClassLevelDependency.class)
public class BothConditionalProduct {}
