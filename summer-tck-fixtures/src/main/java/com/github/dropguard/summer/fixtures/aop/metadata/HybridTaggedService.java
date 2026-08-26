package com.github.dropguard.summer.fixtures.aop.metadata;

/**
 * Row "mixed levels" — the union contract.
 *
 * <p>{@code hybridOp()} carries {@code @MetadataTagged} at the interface-method level while the
 * implementation class carries {@code @ClassMetadataTagged} at the class level; {@code soloOp()} is
 * only covered by the class level. The contract: binding types from both levels are UNIONED per
 * method, and each binding type stays independently visible to its own metadata-driven interceptor.
 * Neither engine currently implements this — the test pins what must be true, not what is.
 */
public interface HybridTaggedService {

    @MetadataTagged
    String hybridOp(String input);

    String soloOp();
}
