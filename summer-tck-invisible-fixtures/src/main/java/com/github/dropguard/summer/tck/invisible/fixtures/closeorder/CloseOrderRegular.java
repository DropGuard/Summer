package com.github.dropguard.summer.tck.invisible.fixtures.closeorder;

import com.github.dropguard.summer.core.Component;

/** A regular (non-produced) AutoCloseable bean — closed after the products. */
@Component
public class CloseOrderRegular implements AutoCloseable {

    @Override
    public void close() {
        CloseOrderRecorder.record("regular");
    }
}
