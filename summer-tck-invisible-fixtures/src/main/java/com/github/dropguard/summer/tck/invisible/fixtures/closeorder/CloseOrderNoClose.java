package com.github.dropguard.summer.tck.invisible.fixtures.closeorder;

import com.github.dropguard.summer.core.Component;

/** A non-AutoCloseable product — the close must skip it without failing. */
@Component
public class CloseOrderNoClose {}
