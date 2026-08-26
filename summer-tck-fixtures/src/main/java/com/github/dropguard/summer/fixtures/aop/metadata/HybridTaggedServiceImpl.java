package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

/** Class-level half of the mixed-level pair — see {@link HybridTaggedService}. */
@Component
@ClassMetadataTagged
public class HybridTaggedServiceImpl implements HybridTaggedService {

    @Override
    public String hybridOp(String input) {
        return "hybrid:" + input;
    }

    @Override
    public String soloOp() {
        return "solo";
    }
}
