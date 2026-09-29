package lab1;

import java.util.concurrent.atomic.AtomicLong;

// Этап 2: 16 локов для корзин, общие атомары.
public final class StripedCollector implements MetricsCollector {
    private static final int GROUPS = 16;

    private final long[] buckets = new long[256];
    private final Object[] locks = new Object[GROUPS];
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong();

    public StripedCollector() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Latency must not be negative");
        }
        int bucket = (int) Math.min(value / 4, 255);
        synchronized (locks[bucket % GROUPS]) {
            buckets[bucket]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    private void updateMin(long value) {
        long old = min.get();
        while (value < old && !min.compareAndSet(old, value)) {
            old = min.get();
        }
    }

    private void updateMax(long value) {
        long old = max.get();
        while (value > old && !max.compareAndSet(old, value)) {
            old = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[256];
        for (int group = 0; group < GROUPS; group++) {
            synchronized (locks[group]) {
                for (int bucket = group; bucket < buckets.length; bucket += GROUPS) {
                    copy[bucket] = buckets[bucket];
                }
            }
        }
        return SnapshotSupport.create(copy, count.get(), sum.get(), min.get(), max.get());
    }
}
