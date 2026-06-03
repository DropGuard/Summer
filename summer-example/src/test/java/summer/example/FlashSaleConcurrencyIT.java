package summer.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import summer.runtime.RuntimeDiEngine;
import summer.test.annotation.SummerTest;

@SummerTest(RuntimeDiEngine.class)
public class FlashSaleConcurrencyIT {

	@Test
	public void testHighConcurrencyFlashSale(FlashSaleService flashSaleService) throws InterruptedException {
		String itemId = "IPHONE-15";
		int totalStock = 100;
		int totalRequests = 10000;

		// 1. Initialize stock in Redis
		flashSaleService.initStock(itemId, totalStock);
		assertEquals(totalStock, flashSaleService.getStock(itemId));

		System.out.println("Starting flash sale! Stock: " + totalStock + ", Requests: " + totalRequests);
		long startTime = System.currentTimeMillis();

		// 2. Setup concurrency synchronization
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(totalRequests);
		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger failureCount = new AtomicInteger(0);

		// 3. Launch 10,000 Virtual Threads
		for (int i = 0; i < totalRequests; i++) {
			Thread.startVirtualThread(() -> {
				try {
					// Wait for the starting gun to ensure maximum concurrency
					startLatch.await();

					// Attempt purchase
					boolean success = flashSaleService.purchase(itemId);
					if (success) {
						successCount.incrementAndGet();
					} else {
						failureCount.incrementAndGet();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					doneLatch.countDown();
				}
			});
		}

		// 4. Fire the starting gun!
		startLatch.countDown();

		// 5. Wait for all 10,000 requests to finish
		doneLatch.await();

		long timeTaken = System.currentTimeMillis() - startTime;
		System.out.println("Flash sale completed in " + timeTaken + " ms.");
		System.out.println("Successful purchases: " + successCount.get());
		System.out.println("Failed purchases (Sold Out): " + failureCount.get());

		// 6. Assertions: We MUST NOT oversell!
		assertEquals(totalStock, successCount.get(), "Success count should exactly match initial stock!");
		assertEquals(0, flashSaleService.getStock(itemId), "Stock should be 0 in Redis!");
		assertEquals(totalRequests - totalStock, failureCount.get(),
				"Failure count should match requests minus stock!");
	}
}
