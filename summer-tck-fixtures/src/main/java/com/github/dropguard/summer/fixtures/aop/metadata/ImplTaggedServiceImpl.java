package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

/** Implementation carrying the binding on the method itself — see {@link ImplTaggedService}. */
@Component
public class ImplTaggedServiceImpl implements ImplTaggedService {

    @Override
    @ClassMetadataTagged
    public String op(String input) {
        return "impl-method:" + input;
    }
}
