package com.github.dropguard.summer.fixtures.di.configprops;

import com.github.dropguard.summer.core.config.ConfigMapping;
import com.github.dropguard.summer.core.config.WithDefault;
import com.github.dropguard.summer.core.config.WithName;
import java.util.List;

/**
 * Test fixture exercising every AOT code-generation branch for the Quarkus-style
 * {@code @ConfigMapping} model: an enum, a {@code List<String>}, an explicit {@code @WithName} key
 * rename, and a {@code @WithDefault}. Used to verify the generated {@code $$ConfigImpl} binds all
 * return types identically under both the Runtime and AOT engines.
 */
@ConfigMapping(prefix = "web")
public interface WebConfig {

    enum RouterType {
        RADIX_TREE,
        LINEAR
    }

    @WithDefault("RADIX_TREE")
    RouterType routerType();

    @WithDefault("")
    List<String> allowedOrigins();

    @WithName("max-conn")
    @WithDefault("100")
    int maxConn();

    String host();
}
