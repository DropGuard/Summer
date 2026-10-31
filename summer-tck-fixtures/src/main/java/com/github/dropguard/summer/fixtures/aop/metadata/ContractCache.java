package com.github.dropguard.summer.fixtures.aop.metadata;

/**
 * The PRODUCT type of a {@code @Bean} method that returns an interface — the natural producer
 * shape. The interface is the bean's lookup key, so a proxy built for the product must implement it
 * (not just its parent interfaces) for {@code getBean(ContractCache)} to resolve and for the
 * binding on {@link #get} to intercept.
 */
public interface ContractCache {

    @MetadataTagged
    String get(String key);
}
