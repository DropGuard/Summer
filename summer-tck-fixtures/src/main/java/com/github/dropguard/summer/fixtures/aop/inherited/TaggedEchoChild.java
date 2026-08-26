package com.github.dropguard.summer.fixtures.aop.inherited;

import com.github.dropguard.summer.core.Component;

/**
 * Case 2: has its own direct interface AND inherits {@code EchoApi} through the superclass chain.
 * The proxy must expose both, and bindings on the inherited interface must still intercept.
 */
@Component
public class TaggedEchoChild extends AbstractEchoBase implements MarkerApi {}
