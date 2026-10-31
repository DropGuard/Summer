package com.github.dropguard.summer.fixtures.aop.metadata;

import com.github.dropguard.summer.core.Component;
import java.math.BigDecimal;
import java.util.List;

/**
 * Substitutes {@code BigDecimal} for {@code T}: the impl's {@code save(BigDecimal)} is NOT the
 * interface's rendered signature ({@code save(java.lang.Number)}), so javac bridges it — the
 * binding keys both engines consult are still built from the INTERFACE methods. The impl class
 * carries no bindings on purpose.
 */
@Component
public class GenericBoundTaggedServiceImpl implements GenericBoundTaggedService<BigDecimal> {

    @Override
    public String save(BigDecimal value) {
        return "saved:" + value;
    }

    @Override
    public String delete(BigDecimal value) {
        return "deleted:" + value;
    }

    @Override
    public String saveAll(List<BigDecimal> values) {
        return "saved-list:" + values;
    }

    @Override
    public String saveAll(BigDecimal[] values) {
        return "saved-array:" + List.of(values);
    }

    @Override
    public String saveAll(BigDecimal[][] values) {
        return "saved-nested:" + values.length;
    }

    @Override
    public <U extends Comparable<U>> String rank(U value) {
        return "ranked:" + value;
    }
}
