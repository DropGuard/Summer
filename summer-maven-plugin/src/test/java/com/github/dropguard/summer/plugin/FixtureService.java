package com.github.dropguard.summer.plugin;

import com.github.dropguard.summer.core.Component;

/** Minimal app bean for the mojo integration test — no web, no config. */
@Component
public class FixtureService {

    public String greet() {
        return "hello";
    }
}
