package summer.issuetracker.common;

import summer.core.Component;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;

/**
 * Snowflake-style distributed id generator (ported from the summer-twitter
 * showcase). Produces time-ordered, collision-free 64-bit ids without a DB
 * sequence — convenient for the demo's hand-written INSERTs.
 */
@Component
public class IdGenerator {

    private static final long EPOCH = 1717200000000L; // 2024-06-01T00:00:00Z
    private static final long WORKER_ID_BITS = 10L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public IdGenerator() {
        this.workerId = createWorkerId();
    }

    public synchronized long nextId() {
        long timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards. Refusing to generate id for " + (lastTimestamp - timestamp) + " ms");
        }
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    private long createWorkerId() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            long hash = 0;
            while (ifaces.hasMoreElements()) {
                byte[] mac = ifaces.nextElement().getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        hash = (hash << 8) | (b & 0xff);
                    }
                }
            }
            return (hash & MAX_WORKER_ID) ^ (new SecureRandom().nextInt((int) MAX_WORKER_ID));
        } catch (Exception e) {
            return new SecureRandom().nextInt((int) MAX_WORKER_ID);
        }
    }
}
