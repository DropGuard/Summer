package com.github.dropguard.summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.ErrorCode;
import org.junit.jupiter.api.Test;

/** Tests for {@link SummerTransactionException}. */
class SummerTransactionExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        SummerTransactionException ex = new SummerTransactionException("Transaction failed");
        assertEquals("Transaction failed", ex.getMessage());
        assertEquals(ErrorCode.TRANSACTION_ERROR, ex.errorCode());
    }

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        SummerTransactionException ex = new SummerTransactionException("Transaction failed", cause);
        assertEquals("Transaction failed", ex.getMessage());
        assertEquals(ErrorCode.TRANSACTION_ERROR, ex.errorCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void shouldBeRuntimeException() {
        SummerTransactionException ex = new SummerTransactionException("Transaction failed");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
