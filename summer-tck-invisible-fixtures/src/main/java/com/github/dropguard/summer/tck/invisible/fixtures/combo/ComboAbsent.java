package com.github.dropguard.summer.tck.invisible.fixtures.combo;

/** A prerequisite that is never a bean, so any {@code @ConditionalOnBean} on it fails. */
public interface ComboAbsent {}
