package com.github.dropguard.summer.tck.aop;

import com.github.dropguard.summer.tck.invisible.fixtures.aop.AopMarker;
import com.github.dropguard.summer.tck.invisible.fixtures.aop.OrphanBoundService;
import com.github.dropguard.summer.tck.invisible.fixtures.aop.OrphanBoundServiceImpl;
import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.DualEngine;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A binding whose interceptor bean is absent from the universe must fail assembly, not silently run
 * the bean un-intercepted. This is the shape every conditional interceptor takes: {@code
 * TransactionInterceptor} is {@code @ConditionalOnBean(TransactionManager.class)}, so a
 * {@code @Transactional} bean in a universe without transaction infrastructure (no DataSource)
 * would previously execute with auto-commit and never roll back — on both engines, silently.
 */
public class OrphanBindingFailFastTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder()
                    .beanClasses(
                            OrphanBoundServiceImpl.class,
                            OrphanBoundService.class,
                            // the binding annotation itself must be indexed; MarkerInterceptor is
                            // deliberately absent — that absence is the case under test
                            AopMarker.class)
                    .shouldFail()
                    .build();

    @DualEngine
    void orphanBindingRejected() {}
}
