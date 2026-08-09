package com.github.dropguard.summer.test;

import com.github.dropguard.summer.core.Internal;

/**
 * A {@link TestResourceManager}'s {@code start()} failed. Distinct from container-assembly
 * failures: a {@code @SummerTest(shouldFail=true)} test promises that ASSEMBLY fails, not that
 * anything throws — a broken external resource (e.g. the database container cannot start) is an
 * infrastructure failure and must always surface, never be swallowed by a negative test's catch.
 */
@Internal
public final class TestResourceStartupException extends RuntimeException {

    public TestResourceStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
