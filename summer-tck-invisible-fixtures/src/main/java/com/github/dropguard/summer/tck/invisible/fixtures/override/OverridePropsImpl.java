package com.github.dropguard.summer.tck.invisible.fixtures.override;

/** A concrete implementation returned by a {@code @Bean} producer for {@link OverrideProps}. */
public class OverridePropsImpl implements OverrideProps {

    @Override
    public String value() {
        return "from-concrete-producer";
    }
}
