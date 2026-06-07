package summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import summer.core.ErrorCode;

/**
 * Tests for {@link SummerTransactionException}.
 */
class SummerTransactionExceptionTest {

	@Test
	void shouldCreateExceptionWithErrorCode() {
		SummerTransactionException ex = new SummerTransactionException(ErrorCode.TRANSACTION_ERROR,
				"Transaction failed");
		assertEquals("Transaction failed", ex.getMessage());
		assertEquals(ErrorCode.TRANSACTION_ERROR, ex.errorCode());
	}

	@Test
	void shouldCreateExceptionWithErrorCodeAndCause() {
		Exception cause = new RuntimeException("Root cause");
		SummerTransactionException ex = new SummerTransactionException(ErrorCode.TRANSACTION_ERROR,
				"Transaction failed", cause);
		assertEquals("Transaction failed", ex.getMessage());
		assertEquals(ErrorCode.TRANSACTION_ERROR, ex.errorCode());
		assertSame(cause, ex.getCause());
	}

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
