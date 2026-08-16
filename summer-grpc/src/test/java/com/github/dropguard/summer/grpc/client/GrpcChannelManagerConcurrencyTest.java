package com.github.dropguard.summer.grpc.client;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.dropguard.summer.grpc.config.GrpcClientTlsConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Concurrency safety for {@link GrpcChannelManager}. This class lives in grpc.client, which is the
 * one package granted an explicit exception to the project's usual ConcurrentHashMap ban (see
 * {@code ArchitectureTest#noConcurrentHashMap}).
 */
class GrpcChannelManagerConcurrencyTest {

    private GrpcChannelManager manager;
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    @AfterEach
    void shutdown() throws Exception {
        if (manager != null) {
            manager.close();
        }
        pool.shutdownNow();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private GrpcClientTlsConfig plaintextConfig() {
        return new GrpcClientTlsConfig() {
            @Override
            public Boolean enabled() {
                return null; // forces plaintext branch, no TLS files required
            }

            @Override
            public String trustCert() {
                return null;
            }
        };
    }

    @Test
    void concurrentGetChannelForSameTargetReturnsSingleChannel() throws Exception {
        manager = new GrpcChannelManager(plaintextConfig());
        final String target = "localhost:12345";
        int threads = 64;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<io.grpc.ManagedChannel>> channelFutures = new ArrayList<>();

        Set<Object> distinctChannels = new HashSet<>();
        for (int i = 0; i < threads; i++) {
            channelFutures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return manager.getChannel(target);
                            }));
        }
        go.countDown();
        for (Future<io.grpc.ManagedChannel> f : channelFutures) {
            distinctChannels.add(f.get(5, TimeUnit.SECONDS));
        }

        // Exactly one channel instance must have been created for the target, no matter the
        // interleaving — a race would have built two (and leaked the discarded one).
        org.junit.jupiter.api.Assertions.assertEquals(
                1, distinctChannels.size(), "concurrent getChannel must yield a single channel");
    }

    @Test
    void concurrentGetChannelForDistinctTargetsAreIndependent() throws Exception {
        manager = new GrpcChannelManager(plaintextConfig());
        String[] targets = {"localhost:9100", "localhost:9101", "localhost:9102", "localhost:9103"};
        CountDownLatch go = new CountDownLatch(1);
        List<Future<io.grpc.ManagedChannel>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            final String t = targets[i % targets.length];
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return manager.getChannel(t);
                            }));
        }
        go.countDown();

        Set<String> authorities = new HashSet<>();
        for (Future<io.grpc.ManagedChannel> f : futures) {
            authorities.add(f.get(5, TimeUnit.SECONDS).authority());
        }
        // All four distinct targets resolved; none crashed the map under concurrent resize.
        org.junit.jupiter.api.Assertions.assertEquals(4, authorities.size());
    }

    @Test
    void closeClearsCacheSoNextGetChannelBuildsFresh() throws Exception {
        manager = new GrpcChannelManager(plaintextConfig());
        io.grpc.ManagedChannel first = manager.getChannel("localhost:7070");
        manager.close();
        manager = new GrpcChannelManager(plaintextConfig());
        io.grpc.ManagedChannel second = manager.getChannel("localhost:7070");
        assertNotSame(first, second, "after close the cache must be cleared");
        assertSame(
                second,
                manager.getChannel("localhost:7070"),
                "new manager still caches per target");
    }
}
