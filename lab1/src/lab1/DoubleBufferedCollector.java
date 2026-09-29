package lab1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// Этап 4: у каждого потока два буфера.
public class DoubleBufferedCollector implements MetricsCollector {
    private static final int NOWHERE = -1;

    private static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = new long[2];
        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    private final Object snapshotLock = new Object();
    private final List<ThreadBuffers> allStates = new ArrayList<>();
    private final ThreadLocal<ThreadBuffers> myState = ThreadLocal.withInitial(() -> {
        ThreadBuffers state = new ThreadBuffers();
        synchronized (snapshotLock) {
            allStates.add(state);
        }
        return state;
    });

    private final boolean repeatActiveCheck;
    private volatile int active;
    private final long[] globalBuckets = new long[256];
    private long globalCount;
    private long globalSum;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax;

    public DoubleBufferedCollector() {
        this(true);
    }

    DoubleBufferedCollector(boolean repeatActiveCheck) {
        this.repeatActiveCheck = repeatActiveCheck;
    }

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Latency must not be negative");
        }
        ThreadBuffers state = myState.get();
        int buffer;
        while (true) {
            buffer = active;
            state.inside.set(buffer);
            if (!repeatActiveCheck || active == buffer) {
                break;
            }
            state.inside.setRelease(NOWHERE);
        }

        try {
            int bucket = (int) Math.min(value / 4, 255);
            state.buckets[buffer][bucket]++;
            state.count[buffer]++;
            state.sum[buffer] += value;
            state.min[buffer] = Math.min(state.min[buffer], value);
            state.max[buffer] = Math.max(state.max[buffer], value);
        } finally {
            state.inside.setRelease(NOWHERE);
        }
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapshotLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers state : allStates) {
                while (state.inside.get() == old) {
                    Thread.onSpinWait();
                }
                collectAndClear(state, old);
            }
            return SnapshotSupport.create(
                    globalBuckets.clone(), globalCount, globalSum, globalMin, globalMax);
        }
    }

    private void collectAndClear(ThreadBuffers state, int buffer) {
        globalCount += state.count[buffer];
        globalSum += state.sum[buffer];
        if (state.count[buffer] != 0) {
            globalMin = Math.min(globalMin, state.min[buffer]);
            globalMax = Math.max(globalMax, state.max[buffer]);
        }
        for (int i = 0; i < globalBuckets.length; i++) {
            globalBuckets[i] += state.buckets[buffer][i];
        }

        Arrays.fill(state.buckets[buffer], 0);
        state.count[buffer] = 0;
        state.sum[buffer] = 0;
        state.min[buffer] = Long.MAX_VALUE;
        state.max[buffer] = 0;
    }
}
