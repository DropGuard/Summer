package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;

/**
 * Gives the previously orphaned {@link MetadataSampleService} pair a bean. The binding annotations
 * live on the INTERFACE methods; this class stays clean — pins that interface-level bindings are
 * discovered through the interface walk (Jandex does not inherit them into the impl) and still
 * reach InterceptedMethod on both engines.
 */
@Component
public class MetadataSampleServiceImpl implements MetadataSampleService {

    @Override
    public String taggedMethod() {
        return "tagged";
    }

    @Override
    public String plainMethod() {
        return "plain";
    }

    @Override
    public String taggedWithArg(String arg) {
        return "tagged:" + arg;
    }
}
