package com.github.dropguard.summer.test;

import java.util.Map;

/**
 * Contract-test resource: records its initArgs, returns overrides keyed by the dotted YAML path
 * (the ConfigBinder.BindingContext contract), and injects a value into a String field. Proves the
 * TestResource machinery — initArgs reach the instance, overrides reach the config binding, and
 * inject() fills the test's fields — without a container.
 */
public class FakeConfigResource implements TestResourceManager {

    static volatile String seenInitArg = "";
    static volatile boolean started = false;

    @Override
    public void init(Map<String, String> initArgs) {
        seenInitArg = initArgs.getOrDefault("probe", "");
    }

    @Override
    public Map<String, String> start() {
        started = true;
        return Map.of("probe.key", seenInitArg, "probe.injected", "from-resource");
    }

    @Override
    public void inject(TestInjector injector) {
        injector.injectIntoFields("injected-value", String.class);
    }

    @Override
    public void stop() {}
}
