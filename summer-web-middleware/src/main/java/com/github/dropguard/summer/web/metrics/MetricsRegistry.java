package com.github.dropguard.summer.web.metrics;

import com.github.dropguard.summer.core.Component;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A central registry for application-level metrics. Provides a simple Prometheus-compatible scrape
 * output.
 */
@Component
public class MetricsRegistry {
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final long startTime = System.currentTimeMillis();

    public void incrementActive() {
        activeRequests.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void decrementActive() {
        activeRequests.decrementAndGet();
    }

    public void recordError() {
        totalErrors.incrementAndGet();
    }

    /** Scrapes the metrics and returns them in Prometheus plain-text format. */
    public String scrape() {
        StringBuilder sb = new StringBuilder();

        sb.append("# HELP summer_requests_active Current number of active requests\n");
        sb.append("# TYPE summer_requests_active gauge\n");
        sb.append("summer_requests_active ").append(activeRequests.get()).append("\n\n");

        sb.append("# HELP summer_requests_total Total number of requests processed\n");
        sb.append("# TYPE summer_requests_total counter\n");
        sb.append("summer_requests_total ").append(totalRequests.get()).append("\n\n");

        sb.append("# HELP summer_errors_total Total number of failed requests\n");
        sb.append("# TYPE summer_errors_total counter\n");
        sb.append("summer_errors_total ").append(totalErrors.get()).append("\n\n");

        sb.append("# HELP summer_uptime_seconds Uptime of the application in seconds\n");
        sb.append("# TYPE summer_uptime_seconds gauge\n");
        sb.append("summer_uptime_seconds ")
                .append((System.currentTimeMillis() - startTime) / 1000.0)
                .append("\n");

        return sb.toString();
    }
}
