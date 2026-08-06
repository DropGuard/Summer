package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.test.annotation.Mock;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a test class's constructor for {@code @Mock}-annotated parameters.
 *
 * <p>Shared by {@link SummerTestLifecycle} (which instantiates the mocks) and {@link AotKey} (which
 * fingerprints the mocked types) — previously each walked the constructor independently, risking
 * divergence between the mocked set used at build time and the one hashed into the cache key.
 */
@Internal
public final class MockedParams {

    private MockedParams() {}

    /**
     * The {@code @Mock}-annotated parameter types of the test's single declared constructor, in
     * parameter order. Empty when the class is null or has zero/multiple constructors.
     */
    public static List<Class<?>> scan(Class<?> testClass) {
        List<Class<?>> mocked = new ArrayList<>();
        if (testClass == null) {
            return mocked;
        }
        Constructor<?>[] ctors = testClass.getDeclaredConstructors();
        if (ctors.length != 1) {
            return mocked;
        }
        Annotation[][] paramAnnotations = ctors[0].getParameterAnnotations();
        Class<?>[] paramTypes = ctors[0].getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            for (Annotation ann : paramAnnotations[i]) {
                if (ann instanceof Mock) {
                    mocked.add(paramTypes[i]);
                    break;
                }
            }
        }
        return mocked;
    }
}
