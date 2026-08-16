package com.github.dropguard.summer.tck.invisible.fixtures.narrow.product;

/**
 * Deliberately package-private {@code @Bean} return type: the AOT engine generates a cross-package
 * reference to every active product class, so this must be rejected with the clear fail-fast
 * message — the runtime engine (reflection-based) would accept it, which is the documented AOT
 * limitation this fixture locks in.
 */
class NonPublicProduct {}
