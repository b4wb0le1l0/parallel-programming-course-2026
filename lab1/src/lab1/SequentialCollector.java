package lab1;

// Этап 0: один поток
public class SequentialCollector implements MetricsCollector {
    private final long[] buckets = new long[256];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Latency must not be negative");
        }
        count++;
        sum += value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
        int bucket = (int) Math.min(value / 4, 255);
        buckets[bucket]++;
    }

    private long percentile(long[] copy, long total, double part) {
        if (total == 0) {
            return 0;
        }

        long target = (long) Math.ceil(total * part);
        long accumulated = 0;
        for (int i = 0; i < copy.length; i++) {
            accumulated += copy[i];
            if (accumulated >= target) {
                return i * 4L;
            }
        }

        throw new IllegalStateException("Record count does not match the bucket total");
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = buckets.clone();

        long p50 = percentile(bucketsCopy, count, 0.50);
        long p99 = percentile(bucketsCopy, count, 0.99);

        return new Snapshot(
                bucketsCopy,
                count,
                sum,
                count == 0 ? 0 : min,
                max,
                p50,
                p99
        );
    }

    public static class main {
        public static void main(String[] args) {
            MetricsCollector collector = new SequentialCollector();
            collector.record(7);
            collector.record(3);

            Snapshot result = collector.snapshot();

            System.out.println(result.count());
            System.out.println(result.sum());
            System.out.println(result.min());
            System.out.println(result.max());
            System.out.println(result.p50());
            System.out.println(result.p99());
        }
    }
}
