package lab1;

final class SnapshotSupport {
    private SnapshotSupport() {
    }

    static long percentile(long[] buckets, long count, double part) {
        if (count == 0) {
            return 0;
        }
        long target = (long) Math.ceil(count * part);
        long accumulated = 0;
        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];
            if (accumulated >= target) {
                return i * 4L;
            }
        }

        return 255 * 4L;
    }

    static Snapshot create(long[] buckets, long count, long sum, long min, long max) {
        long visibleMin = count == 0 ? 0 : min;
        long visibleMax = count == 0 ? 0 : max;
        return new Snapshot(
                buckets,
                count,
                sum,
                visibleMin,
                visibleMax,
                percentile(buckets, count, 0.50),
                percentile(buckets, count, 0.99)
        );
    }
}
