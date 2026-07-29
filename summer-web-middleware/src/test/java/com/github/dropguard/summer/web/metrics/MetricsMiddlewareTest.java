package com.github.dropguard.summer.web.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpMethod;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.Request;
import org.junit.jupiter.api.Test;

/** Tests for {@link MetricsMiddleware}. */
class MetricsMiddlewareTest {

    @Test
    void shouldIncrementAndDecrementActiveRequests() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsMiddleware middleware = new MetricsMiddleware(registry);

        Handler handler =
                middleware.apply(
                        ctx -> {
                            ctx.status(HttpStatus.OK);
                        });

        Request request = new Request(HttpMethod.GET, "/test", null, null, new byte[0]);
        HttpContext ctx = new HttpContext(request);
        handler.handle(ctx);

        // After the handler completes, active should be back to zero
        String scrape = registry.scrape();
        assertTrue(scrape.contains("summer_requests_active 0"), "Active requests should be 0");
        assertTrue(scrape.contains("summer_requests_total 1"), "Total requests should be 1");
        assertTrue(scrape.contains("summer_errors_total 0"), "Total errors should be 0");
    }

    @Test
    void shouldRecordErrorOnStatus500() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsMiddleware middleware = new MetricsMiddleware(registry);

        Handler handler =
                middleware.apply(
                        ctx -> {
                            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                        });

        Request request = new Request(HttpMethod.GET, "/test", null, null, new byte[0]);
        HttpContext ctx = new HttpContext(request);
        handler.handle(ctx);

        String scrape = registry.scrape();
        assertTrue(scrape.contains("summer_requests_active 0"), "Active requests should be 0");
        assertTrue(scrape.contains("summer_requests_total 1"), "Total requests should be 1");
        assertTrue(scrape.contains("summer_errors_total 1"), "Total errors should be 1");
    }

    @Test
    void shouldRecordErrorOnException() {
        MetricsRegistry registry = new MetricsRegistry();
        MetricsMiddleware middleware = new MetricsMiddleware(registry);

        Handler handler =
                middleware.apply(
                        ctx -> {
                            throw new RuntimeException("boom");
                        });

        Request request = new Request(HttpMethod.GET, "/test", null, null, new byte[0]);
        HttpContext ctx = new HttpContext(request);

        assertThrows(RuntimeException.class, () -> handler.handle(ctx));

        String scrape = registry.scrape();
        assertTrue(scrape.contains("summer_requests_active 0"), "Active requests should be 0");
        assertTrue(scrape.contains("summer_errors_total 1"), "Total errors should be 1");
    }

    @Test
    void shouldContainUptimeMetric() {
        MetricsRegistry registry = new MetricsRegistry();
        String scrape = registry.scrape();

        assertTrue(scrape.contains("summer_uptime_seconds"), "Uptime metric should be present");
        assertTrue(
                scrape.contains("summer_uptime_seconds 0."),
                "Fresh registry should have near-zero uptime");
    }
}
