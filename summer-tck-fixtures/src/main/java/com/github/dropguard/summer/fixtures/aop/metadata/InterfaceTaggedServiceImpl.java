package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

/** Clean implementation of the interface-annotated {@link InterfaceTaggedService}. */
@Component
public class InterfaceTaggedServiceImpl implements InterfaceTaggedService {

    @Override
    public String ifaceOp(String input) {
        return "iface-class:" + input;
    }
}
