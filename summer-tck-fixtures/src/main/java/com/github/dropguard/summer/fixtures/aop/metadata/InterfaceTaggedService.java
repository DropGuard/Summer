package com.github.dropguard.summer.fixtures.aop.metadata;

/**
 * Row "class-level on the interface": the binding sits on the interface TYPE; both members are
 * clean. Pins that interface-declared class bindings land in the {@code ""} (class-level) key via
 * the interface walk and are materialised for every method.
 */
@ClassMetadataTagged
public interface InterfaceTaggedService {

    String ifaceOp(String input);
}
