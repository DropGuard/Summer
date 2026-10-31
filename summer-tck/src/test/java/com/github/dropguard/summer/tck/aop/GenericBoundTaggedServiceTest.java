package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.metadata.GenericBoundTaggedService;
import com.github.dropguard.summer.fixtures.aop.metadata.MethodTaggedRecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.math.BigDecimal;
import java.util.List;

/**
 * Pins the overload-exact binding key's type-variable rendering, dual-engine: Runtime keys from the
 * erased parameter, enrichment/AOT from Jandex {@code Type#name()} — the two coincide for every
 * expressible shape because a type variable's erasure IS its leftmost bound, which is exactly what
 * Jandex {@code TypeVariable#name()} returns. Each call below is one cell of that matrix (see
 * {@link GenericBoundTaggedService}); {@code delete(T)} stays unannotated and must not intercept.
 */
@SummerTest
public class GenericBoundTaggedServiceTest {

    @DualEngine
    void typeVariableParametersRenderIdenticallyOnBothEngines(BeanContainer context) {
        MethodTaggedRecordingInterceptor interceptor =
                context.getBean(MethodTaggedRecordingInterceptor.class);
        interceptor.clearLog();

        @SuppressWarnings("unchecked")
        GenericBoundTaggedService<BigDecimal> service =
                context.getBean(GenericBoundTaggedService.class);

        assertEquals("saved:1", service.save(BigDecimal.ONE));
        assertEquals("deleted:1", service.delete(BigDecimal.ONE));
        assertEquals("saved-list:[1]", service.saveAll(List.of(BigDecimal.ONE)));
        assertEquals("saved-array:[1]", service.saveAll(new BigDecimal[] {BigDecimal.ONE}));
        assertEquals("saved-nested:1", service.saveAll(new BigDecimal[][] {{BigDecimal.ONE}}));
        assertEquals("ranked:1", service.rank(BigDecimal.ONE));

        assertEquals(
                List.of(
                        "record:save",
                        "record:saveAll",
                        "record:saveAll",
                        "record:saveAll",
                        "record:rank"),
                interceptor.getCallLog(),
                "every annotated type-variable shape must intercept and delete(T) must not — on "
                        + "BOTH engines");
    }
}
