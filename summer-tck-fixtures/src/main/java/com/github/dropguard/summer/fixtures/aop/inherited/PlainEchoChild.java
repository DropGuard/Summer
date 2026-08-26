package com.github.dropguard.summer.fixtures.aop.inherited;

import com.github.dropguard.summer.core.Component;

/** Case 1: no interfaces of its own — everything arrives via the superclass chain. */
@Component
public class PlainEchoChild extends AbstractEchoBase {}
