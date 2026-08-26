package com.github.dropguard.summer.fixtures.aop.metadata;

/** Interface for {@link ClassTaggedServiceImpl}, kept clean of annotations on purpose. */
public interface ClassTaggedService {

    String taggedOp(String input);

    String plainOp();
}
