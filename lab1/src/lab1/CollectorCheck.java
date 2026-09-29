package lab1;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public final class CollectorCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkSnapshot(Snapshot s) {
        long bucketTotal = 0;
        for (long bucket : s.buckets()) bucketTotal += bucket;
        check(s.buckets().length == 256, "Expected 256 buckets");
        check(bucketTotal == s.count(), "Bucket total does not match count");
    }

    private static void basic(MetricsCollector collector) {
        Snapshot empty = collector.snapshot();
        checkSnapshot(empty);
        check(empty.count() == 0 && empty.sum() == 0 && empty.min() == 0
                && empty.max() == 0 && empty.p50() == 0 && empty.p99() == 0, "Invalid empty snapshot");
        for (long value : new long[]{0, 3, 4, 7, 8}) collector.record(value);
        Snapshot s = collector.snapshot();
        checkSnapshot(s);
        check(s.count() == 5 && s.sum() == 22 && s.min() == 0 && s.max() == 8, "Invalid summary");
        check(s.buckets()[0] == 2 && s.buckets()[1] == 2 && s.buckets()[2] == 1, "Invalid buckets");
        check(s.p50() == 4 && s.p99() == 8, "Invalid percentiles");
        collector.record(1);
        check(s.buckets()[0] == 2 && s.count() == 5, "An old snapshot was modified");
        s.buckets()[0] = 123;
        check(collector.snapshot().buckets()[0] == 3, "A snapshot modified the internal array");
        long count = collector.snapshot().count();
        for (long invalid : new long[]{-1, -4, Long.MIN_VALUE}) {
            boolean rejected = false;
            try {
                collector.record(invalid);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "A negative value must be rejected");
        }
        check(collector.snapshot().count() == count, "Invalid input changed count");
    }

    private static void bounds(MetricsCollector collector) {
        for (long value : new long[]{1023, 1024, 5000}) collector.record(value);
        Snapshot s = collector.snapshot();
        checkSnapshot(s);
        check(s.buckets()[255] == 3 && s.sum() == 7047 && s.min() == 1023 && s.max() == 5000, "Invalid last bucket");
        check(s.p50() == 1020 && s.p99() == 1020, "Invalid last bucket boundary");
    }

    private static void concurrent() throws InterruptedException {
        MetricsCollector collector = new LockedCollector();
        int workers = 4;
        int calls = 100_000;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[workers];
        Throwable[] errors = new Throwable[workers];
        for (int k = 0; k < workers; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < calls; i++) collector.record(7);
                } catch (Throwable e) {
                    errors[id] = e;
                }
            });
            threads[k].start();
        }
        ready.await();
        start.countDown();
        try {
            for (int i = 0; i < 10_000; i++) {
                Snapshot s = collector.snapshot();
                checkSnapshot(s);
                check(s.sum() == 7 * s.count(), "Inconsistent count and sum");
                if (s.count() != 0) {
                    check(s.min() == 7 && s.max() == 7 && s.p50() == 4 && s.p99() == 4,
                            "Inconsistent snapshot");
                }
            }
        } finally {
            for (Thread thread : threads) thread.join();
        }
        for (Throwable error : errors) {
            if (error != null) throw new AssertionError("Worker failed", error);
        }
        Snapshot end = collector.snapshot();
        checkSnapshot(end);
        check(end.count() == (long) workers * calls && end.sum() == 7L * workers * calls, "Lost records");
        System.out.println("PASS: 4 writers, 10000 snapshots, final count=" + end.count());
    }

    public static void main(String[] args) throws InterruptedException {
        MetricsCollector[] collectors = {
                new SequentialCollector(),
                new LockedCollector(),
                new StripedCollector(),
                new ThreadLocalCollector(),
                new DoubleBufferedCollector()
        };
        for (MetricsCollector collector : collectors) {
            basic(collector);
        }
        for (MetricsCollector collector : new MetricsCollector[]{
                new SequentialCollector(),
                new LockedCollector(),
                new StripedCollector(),
                new ThreadLocalCollector(),
                new DoubleBufferedCollector()
        }) {
            bounds(collector);
        }
        System.out.println("PASS: all stages: summary, percentiles, empty snapshot, copies, input bounds");
        long[] first = LoadGenerator.generate();
        long[] second = LoadGenerator.generate();
        check(first.length == 1 << 20, "Invalid load size");
        check(Arrays.equals(first, second), "The same seed must produce the same values");
        for (long value : first) check(value >= 1 && value <= 1023, "Value outside the load range");
        System.out.println("PASS: load size, range, reproducibility");
        concurrent();
        MetricsCollector empty = new EmptyLockCollector();
        empty.record(7);
        check(empty.snapshot().count() == 0, "The empty-lock collector must not store records");
        System.out.println("ALL CHECKS PASSED");
    }
}
