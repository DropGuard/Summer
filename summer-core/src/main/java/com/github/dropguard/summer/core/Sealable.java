package com.github.dropguard.summer.core;

/**
 * Container-driven post-assembly boundary for beans that must stop accepting writes once the
 * container is built.
 *
 * <p>The container calls {@link #seal()} exactly once per registered instance, at the end of {@link
 * BeanContainer.Builder#build(Engine)} — after every bean is instantiated, every
 * {@code @PostConstruct} has run, and every validator has passed. This is the unified "assembly
 * complete" signal: a bean declares its assembly-time writes (mutators guarded by {@link
 * FrozenState}, or final fields) and the container provides the timing, so a bean never has to
 * coordinate with its own last assembly-time writer.
 *
 * <p>{@code seal()} must be idempotent — the container calls it once, but a bean may also seal
 * itself earlier (e.g. from its own {@code @PostConstruct}), and the container may hold both an AOP
 * proxy and the raw instance.
 */
public interface Sealable {

    /** Stops this bean's assembly-time writes. Idempotent. */
    void seal();
}
