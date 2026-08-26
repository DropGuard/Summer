package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.metadata.ClassTaggedService;
import com.github.dropguard.summer.fixtures.aop.metadata.HybridTaggedService;
import com.github.dropguard.summer.fixtures.aop.metadata.ImplTaggedService;
import com.github.dropguard.summer.fixtures.aop.metadata.InterfaceTaggedService;
import com.github.dropguard.summer.fixtures.aop.metadata.MetadataRecordingInterceptor;
import com.github.dropguard.summer.fixtures.aop.metadata.MetadataSampleService;
import com.github.dropguard.summer.fixtures.aop.metadata.MethodTaggedRecordingInterceptor;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;
import java.util.List;

/**
 * THE contract for binding metadata: whatever the declaration site (implementation class,
 * implementation method, interface class, interface method — alone or in combination), a binding
 * that applies to a method must be visible to that method's {@code InterceptedMethod}, so
 * metadata-driven interceptors ({@code isAnnotationPresent}-style, like TransactionInterceptor)
 * observe identical behaviour on both engines.
 *
 * <p>Chain routing alone does not prove this: an engine may route every bound method through the
 * chain yet hand the interceptor an empty annotation set. Each method below pins one cell of the
 * declaration-site matrix; mixed levels pin union semantics with per-binding independence.
 */
@SummerTest
public class BindingMetadataBehaviorTest {

    /**
     * The container must arrive as a method parameter: {@code @DualEngine} invocations share one
     * test instance, and constructor-injected containers are always the RUNTIME build. Only the
     * parameter resolves to the per-invocation engine's container (see
     * DualEngineInvocationProvider).
     */

    // ── Row 1: class-level on the implementation class ────────────────

    @DualEngine
    void implClassLevelBindingIsVisibleToMetadataDrivenInterceptors(BeanContainer context) {
        MetadataRecordingInterceptor interceptor =
                context.getBean(MetadataRecordingInterceptor.class);
        interceptor.clearLog();

        ClassTaggedService service = context.getBean(ClassTaggedService.class);
        assertEquals("tagged:row-1", service.taggedOp("row-1"));

        assertEquals(
                List.of("record:taggedOp"),
                interceptor.getCallLog(),
                "A class-level-bound method must carry its binding types in "
                        + "InterceptedMethod so isAnnotationPresent-driven interceptors fire");
    }

    @DualEngine
    void everyMethodOfAClassLevelBoundBeanCarriesTheBinding(BeanContainer context) {
        MetadataRecordingInterceptor interceptor =
                context.getBean(MetadataRecordingInterceptor.class);
        interceptor.clearLog();

        ClassTaggedService service = context.getBean(ClassTaggedService.class);
        assertEquals("plain", service.plainOp());

        assertEquals(
                List.of("record:plainOp"),
                interceptor.getCallLog(),
                "Class-level binding means every method of the bean carries the "
                        + "binding semantically — not just that the chain routes them");
    }

    // ── Row 2: method-level on the implementation class ───────────────

    @DualEngine
    void implMethodLevelBindingIsVisibleToMetadataDrivenInterceptors(BeanContainer context) {
        MetadataRecordingInterceptor interceptor =
                context.getBean(MetadataRecordingInterceptor.class);
        interceptor.clearLog();

        ImplTaggedService service = context.getBean(ImplTaggedService.class);
        assertEquals("impl-method:row-2", service.op("row-2"));

        assertEquals(
                List.of("record:op"),
                interceptor.getCallLog(),
                "An impl-declared method binding must reach InterceptedMethod even "
                        + "though the proxy dispatches through the clean interface");
    }

    // ── Row 3: method-level on the interface ──────────────────────────

    @DualEngine
    void interfaceMethodLevelBindingIsVisibleToMetadataDrivenInterceptors(BeanContainer context) {
        MethodTaggedRecordingInterceptor interceptor =
                context.getBean(MethodTaggedRecordingInterceptor.class);
        interceptor.clearLog();

        MetadataSampleService service = context.getBean(MetadataSampleService.class);
        assertEquals("tagged:x", service.taggedWithArg("x"));
        assertEquals("plain", service.plainMethod());

        assertEquals(
                List.of("record:taggedWithArg"),
                interceptor.getCallLog(),
                "Interface-method bindings are inherited by the impl but not by "
                        + "Jandex reflection — they must still be materialised");
    }

    // ── Row 4: class-level on the interface ───────────────────────────

    @DualEngine
    void interfaceClassLevelBindingCoversAllMethods(BeanContainer context) {
        MetadataRecordingInterceptor interceptor =
                context.getBean(MetadataRecordingInterceptor.class);
        interceptor.clearLog();

        InterfaceTaggedService service = context.getBean(InterfaceTaggedService.class);
        assertEquals("iface-class:row-4", service.ifaceOp("row-4"));

        assertEquals(
                List.of("record:ifaceOp"),
                interceptor.getCallLog(),
                "A binding declared on the interface type applies to every method "
                        + "and must be visible in InterceptedMethod like an impl-class binding");
    }

    // ── Row 5: mixed levels — union semantics, per-binding independence ──

    @DualEngine
    void mixedLevelsUnionPerMethodWithIndependentBindings(BeanContainer context) {
        MetadataRecordingInterceptor classLevelInterceptor =
                context.getBean(MetadataRecordingInterceptor.class);
        MethodTaggedRecordingInterceptor methodLevelInterceptor =
                context.getBean(MethodTaggedRecordingInterceptor.class);
        classLevelInterceptor.clearLog();
        methodLevelInterceptor.clearLog();

        HybridTaggedService service = context.getBean(HybridTaggedService.class);

        assertEquals("hybrid:row-5", service.hybridOp("row-5"));
        assertEquals(
                List.of("record:hybridOp"),
                classLevelInterceptor.getCallLog(),
                "The class-level binding covers hybridOp regardless of the extra "
                        + "method-level binding");
        assertEquals(
                List.of("record:hybridOp"),
                methodLevelInterceptor.getCallLog(),
                "The interface-method binding must stay independently visible on a "
                        + "method that also falls under a class-level binding");

        classLevelInterceptor.clearLog();
        methodLevelInterceptor.clearLog();

        assertEquals("solo", service.soloOp());
        assertEquals(List.of("record:soloOp"), classLevelInterceptor.getCallLog());
        assertTrue(
                methodLevelInterceptor.getCallLog().isEmpty(),
                "soloOp carries no method-level binding — its interceptor must not fire");
    }
}
