package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.RuntimeDiMarker;
import com.github.dropguard.summer.core.annotation.ConditionalOnBean;

/**
 * Runtime-only assembly-time fill for the subclass regression: registers a hand-written {@code
 * RowMapper} for the seeded {@link SubclassUser} on the {@link UserTemplate} product. On the AOT
 * engine the {@code @ConditionalOnBean(RuntimeDiMarker)} gate is unsatisfied (no {@code
 * RuntimeDiMarker} synthetic) and this bean is dropped, leaving the {@code @RowModel} bake as the
 * only mapper source — the same gate the framework's real registrar relies on. The mapper is
 * hand-written with direct class references (no {@code Class.forName}, no index scan): the
 * reflective index-scanning mechanism is data-jdbc's registrar implementation, not what this
 * regression tests.
 */
@Component
@ConditionalOnBean(RuntimeDiMarker.class)
public class RuntimeRowMapperNarrowFill {

    public RuntimeRowMapperNarrowFill(UserTemplate userTemplate) {
        userTemplate.registerMapper(
                SubclassUser.class,
                (rs, rowNum) -> new SubclassUser(rs.getInt("id"), rs.getString("name")));
    }
}
