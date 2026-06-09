package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeApplicationContext;
import summer.test.annotation.SummerTest;

@SummerTest(RuntimeApplicationContext.class)
public class FlashSaleConcurrencyIT {

	@Test
	void shouldNotOversell(FlashSaleService service) throws Exception {
		String itemId = "IPHONE-15";
		int stock = 100;
		int requests = 10000;

		service.initStock(itemId, stock);
		assertEquals(stock, service.getStock(itemId));

		AtomicInteger successCount = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(requests);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(requests);

		for (int i = 0; i < requests; i++) {
			Thread.startVirtualThread(() -> {
				ready.countDown();
				try {
					start.await();
					if (service.purchase(itemId)) {
						successCount.incrementAndGet();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();

		assertEquals(stock, successCount.get(), "Should not oversell");
		assertEquals(0, service.getStock(itemId), "Stock should be 0");
	}
}
