package com.github.dropguard.summer.tck.invisible.fixtures.conditionalproducer;

/** A prerequisite that is never a bean, so any {@code @ConditionalOnBean} on it fails. */
public interface AbsentMarker {}
