package com.github.dropguard.summer.aot.testfixtures.validation;

/**
 * Bean targeted by {@link BadBeanValidator}. Test fixture for the AOT-side validation-phase codegen
 * test: this type is the {@code targetType} of the validator, so the generated {@code build()}
 * method must walk every Validator bean in {@code builder.singletons()}, look up targets via {@code
 * builder.getBeans(BadBean.class)}, and feed each one to {@code validate}.
 */
public final class BadBean {

    private final String name;

    public BadBean(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
