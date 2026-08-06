package com.github.dropguard.summer.fixtures.di.conditional;

import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/**
 * Negative AND-semantics fixture: the class declares a prerequisite that never exists, while the
 * producer method's own condition is satisfied. The bean must be excluded — a single-slot evaluator
 * that let the method-level condition overwrite the class-level one would wrongly register it.
 */
@ConditionalOnBean(MissingClassDependency.class)
public class MethodOnlySatisfiedProduct {}
