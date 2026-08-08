package com.github.dropguard.summer.tck.negative.fixtures.di;

/**
 * Package-private {@code @Bean} product: the discovery must reject it with a clear error — the AOT
 * wiring references the product class from another package, and a non-public type is not accessible
 * cross-package (a latent production breakage).
 */
class PackagePrivateProduct {}
