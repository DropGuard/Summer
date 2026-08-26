package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

/**
 * Test fixture: {@code @ClassMetadataTagged} on the CLASS, interface clean.
 *
 * <p>Class-level binding means every method of the bean carries the binding semantically. The proxy
 * must therefore not only route every method through the chain (which both engines already do) but
 * also materialise the binding types into each method's {@code InterceptedMethod} so that
 * metadata-driven interceptors see them. The Runtime engine derives this from the implementation
 * class; the AOT engine must emit it as a compile-time constant per method.
 */
@Component
@ClassMetadataTagged
public class ClassTaggedServiceImpl implements ClassTaggedService {

    @Override
    public String taggedOp(String input) {
        return "tagged:" + input;
    }

    @Override
    public String plainOp() {
        return "plain";
    }
}
