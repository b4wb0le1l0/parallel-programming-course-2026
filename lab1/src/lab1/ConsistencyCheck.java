package lab1;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

// Стресс-тест этапов 2–4: снимки делаются одновременно с записью.
public final class ConsistencyCheck {
    private static final class StopSignal {
        volatile boolean stop;
    }

    private record Result(long broken, long less, long greater,
                          long finalDifference, long calls, long finalCount) {
    }

    private static MetricsCollector create(String variant) {
        return switch (variant) {
            case "striped" -> new StripedCollector();
            case "thread-local" -> new ThreadLocalCollector();
            case "double-buffered" -> new DoubleBufferedCollector();
            case "double-buffered-broken" -> new BrokenDoubleBufferedCollector();
            default -> throw new IllegalArgumentException(
                    "Variants: striped, thread-local, double-buffered, double-buffered-broken");
        };
    }

    private static long bucketTotal(Snapshot snapshot) {
        long total = 0;
        for (long bucket : snapshot.buckets()) {
            total += bucket;
        }
        return total;
    }

    private static Result run(MetricsCollector collector, int snapshotCount)
            throws InterruptedException {
        int workers = 4;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        StopSignal signal = new StopSignal();
        Thread[] threads = new Thread[workers];
        long[] calls = new long[workers];
        Throwable[] errors = new Throwable[workers];

        for (int k = 0; k < workers; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                long local = 0;
                ready.countDown();
                try {
                    start.await();
                    while (!signal.stop) {
                        collector.record(7);
                        local++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors[id] = e;
                } catch (Throwable e) {
                    errors[id] = e;
                    signal.stop = true;
                } finally {
                    calls[id] = local;
                }
            }, "stress-writer-" + id);
            threads[k].start();
        }

        long broken = 0;
        long less = 0;
        long greater = 0;
        try {
            ready.await();
            start.countDown();
            for (int i = 0; i < snapshotCount; i++) {
                Snapshot snapshot = collector.snapshot();
                long buckets = bucketTotal(snapshot);
                if (buckets != snapshot.count()) {
                    broken++;
                    if (buckets < snapshot.count()) less++;
                    else greater++;
                }
            }
        } finally {
            signal.stop = true;
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
        }
        for (int i = 0; i < workers; i++) {
            if (errors[i] != null) {
                throw new IllegalStateException("Worker " + i + " failed", errors[i]);
            }
        }
        long totalCalls = 0;
        for (long value : calls) totalCalls += value;
        Snapshot end = collector.snapshot();
        return new Result(broken, less, greater,
                end.count() - totalCalls, totalCalls, end.count());
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("ConsistencyCheck variant [snapshots=10000]");
        }
        String variant = args[0];
        int snapshots = args.length == 2 ? Integer.parseInt(args[1]) : 10_000;
        Result result = run(create(variant), snapshots);
        double percent = 100.0 * result.broken() / snapshots;
        System.out.println("variant,snapshots,broken,broken_percent,less,greater,final_difference,calls,final_count");
        System.out.printf(Locale.ROOT, "%s,%d,%d,%.4f,%d,%d,%d,%d,%d%n",
                variant, snapshots, result.broken(), percent, result.less(), result.greater(),
                result.finalDifference(), result.calls(), result.finalCount());

        if (variant.equals("double-buffered")
                && (result.broken() != 0 || result.finalDifference() != 0)) {
            throw new AssertionError("The correct double-buffered collector violated consistency");
        }
    }
}
