package com.github.dropguard.summer.tck.grpc;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * gRPC infrastructure wiring, verified on BOTH DI engines via {@link DualEngine}.
 *
 * <p>Previously split into {@code RuntimeGrpcTCKTest} / {@code AotGrpcTCKTest} siblings that each
 * supplied a {@code createContext()} factory — a manual dual-engine pattern the framework has since
 * replaced with {@code @DualEngine}. The container now comes from the {@code @SummerTest} injection
 * contract; no engine-specific subclass is needed.
 */
@SummerTest
public class GrpcBehaviorTest extends AbstractGrpcTCK {

    public GrpcBehaviorTest(BeanContainer context) {
        super(context);
    }
}
