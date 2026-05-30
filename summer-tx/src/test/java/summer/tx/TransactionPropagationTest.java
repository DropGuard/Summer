package summer.tx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TransactionPropagation} enum.
 */
class TransactionPropagationTest {

	@Test
	void shouldHaveRequiredPropagation() {
		TransactionPropagation propagation = TransactionPropagation.REQUIRED;
		assertNotNull(propagation);
		assertEquals("REQUIRED", propagation.name());
	}

	@Test
	void shouldHaveRequiresNewPropagation() {
		TransactionPropagation propagation = TransactionPropagation.REQUIRES_NEW;
		assertNotNull(propagation);
		assertEquals("REQUIRES_NEW", propagation.name());
	}

	@Test
	void shouldHaveTwoValues() {
		TransactionPropagation[] values = TransactionPropagation.values();
		assertEquals(2, values.length);
	}

	@Test
	void shouldSupportValueOf() {
		assertEquals(TransactionPropagation.REQUIRED, TransactionPropagation.valueOf("REQUIRED"));
		assertEquals(TransactionPropagation.REQUIRES_NEW, TransactionPropagation.valueOf("REQUIRES_NEW"));
	}

	@Test
	void shouldThrowOnInvalidValue() {
		assertThrows(IllegalArgumentException.class, () -> TransactionPropagation.valueOf("INVALID"));
	}

	@Test
	void shouldSupportOrdinal() {
		assertEquals(0, TransactionPropagation.REQUIRED.ordinal());
		assertEquals(1, TransactionPropagation.REQUIRES_NEW.ordinal());
	}
}