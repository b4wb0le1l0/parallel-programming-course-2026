package lab1;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class Bench {
    private static final class StopSignal {
        volatile boolean stop;
    }

    private record Run(long operations, long nanos) {
        double rate() {
            return operations / (nanos / 1_000_000_000.0);
        }
    }

    private static MetricsCollector create(String variant) {
        return switch (variant) {
            case "sequential" -> new SequentialCollector();
            case "locked" -> new LockedCollector();
            case "empty" -> new EmptyLockCollector();
            case "striped" -> new StripedCollector();
            case "thread-local" -> new ThreadLocalCollector();
            case "double-buffered" -> new DoubleBufferedCollector();
            default -> throw new IllegalArgumentException(
                    "Variants: sequential, locked, empty, striped, thread-local, double-buffered");
        };
    }

    private static Run run(MetricsCollector collector, long[] values,
                           int workers, long millis) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        StopSignal signal = new StopSignal();
        Thread[] threads = new Thread[workers];
        long[] operations = new long[workers];
        Throwable[] errors = new Throwable[workers];

        for (int k = 0; k < workers; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int index = (id * 1000) % values.length;
                ready.countDown();
                try {
                    start.await();
                    while (!signal.stop) {
                        collector.record(values[index]);
                        localCount++;
                        index++;
                        if (index == values.length) index = 0;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors[id] = e;
                } catch (Throwable e) {
                    errors[id] = e;
                    signal.stop = true;
                } finally {
                    operations[id] = localCount;
                }
            }, "bench-" + id);
            threads[k].start();
        }

        long t0 = 0;
        long t1 = 0;
        try {
            ready.await();
            t0 = System.nanoTime();
            start.countDown();
            Thread.sleep(millis);
        } finally {
            signal.stop = true;
            t1 = System.nanoTime();
            start.countDown(); // освобождает ожидающих и при прерывании main
            joinAll(threads);
        }
        long total = 0;
        for (int i = 0; i < workers; i++) {
            if (errors[i] != null) throw new IllegalStateException("Worker " + i + " failed", errors[i]);
            total += operations[i];
        }
        return new Run(total, t1 - t0);
    }

    // Даже при прерывании main сначала дожидаемся уже остановленных рабочих.
    private static void joinAll(Thread[] threads) throws InterruptedException {
        boolean interrupted = false;
        for (Thread thread : threads) {
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Interrupted while waiting for workers");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 1 && args[0].equals("--info")) {
            System.out.println("java=" + System.getProperty("java.version"));
            System.out.println("vm=" + System.getProperty("java.vm.name"));
            System.out.println("os=" + System.getProperty("os.name"));
            System.out.println("arch=" + System.getProperty("os.arch"));
            System.out.println("processors=" + Runtime.getRuntime().availableProcessors());
            return;
        }

        if (args.length < 2 || args.length > 4) {
            throw new IllegalArgumentException(
                    "Bench sequential|locked|empty|striped|thread-local|double-buffered threads [seconds=5] [repeats=5]");
        }
        String variant = args[0];
        int workers = Integer.parseInt(args[1]);
        double seconds = args.length >= 3 ? Double.parseDouble(args[2]) : 5;
        int repeats = args.length >= 4 ? Integer.parseInt(args[3]) : 5;
        if (workers < 1 || workers > Runtime.getRuntime().availableProcessors()) {
            throw new IllegalArgumentException("Thread count must be between 1 and the number of available processors");
        }
        if (variant.equals("sequential") && workers != 1) {
            throw new IllegalArgumentException("SequentialCollector supports exactly one thread");
        }
        if (!Double.isFinite(seconds) || seconds < 0.001 || seconds > 3600 || repeats < 1) {
            throw new IllegalArgumentException("Expected 0.001 <= seconds <= 3600 and repeats >= 1");
        }
        long millis = Math.round(seconds * 1000);
        MetricsCollector collector = create(variant);
        long[] values = LoadGenerator.generate(); // до старта секундомера
        System.err.printf(Locale.ROOT, "%s, T=%d: warmup %.3f s, %d repeats%n", variant, workers, seconds, repeats);
        Run warmup = run(collector, values, workers, millis);
        long expectedCount = warmup.operations();
        double[] rates = new double[repeats];
        System.out.println("variant,threads,trial,operations,seconds,ops_per_second");
        for (int trial = 0; trial < repeats; trial++) {
            Run result = run(collector, values, workers, millis);
            expectedCount += result.operations();
            rates[trial] = result.rate();
            System.out.printf(Locale.ROOT, "%s,%d,%d,%d,%.9f,%.3f%n",
                    variant, workers, trial + 1, result.operations(), result.nanos() / 1e9, result.rate());
        }
        Snapshot snapshot = collector.snapshot();
        long bucketTotal = 0;
        for (long bucket : snapshot.buckets()) bucketTotal += bucket;
        if (!variant.equals("empty") && (snapshot.count() != expectedCount || bucketTotal != expectedCount)) {
            throw new AssertionError("Final collector count does not match the number of calls");
        }
        Arrays.sort(rates);
        double median = repeats % 2 == 1 ? rates[repeats / 2]
                : (rates[repeats / 2 - 1] + rates[repeats / 2]) / 2;
        System.out.printf(Locale.ROOT, "MEDIAN,%s,%d,%.3f%n", variant, workers, median);
        System.err.println("snapshot.count=" + snapshot.count() + ", calls including warmup=" + expectedCount);
    }
}
