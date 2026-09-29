package practice;

import java.util.ArrayList;
import java.util.List;

public class S01Syntax {
    interface Accumulator {
        void add(long value);
        long total();
    }

    static final class Sum implements Accumulator {
        private long total;

        @Override
        public void add(long value) {
            total += value;
        }

        @Override
        public long total() {
            return total;
        }
    }

    record Result(long count, long sum) {
        double average() {
            return count == 0 ? 0.0 : (double) sum / count;
        }
    }

    public static long Max(long[] values) {
        if (values.length == 0) {
            System.out.println("Empty array");
            return 0;
        }

        long max = values[0];
        for (int i = 0; i < values.length; i++) {
            if (values[i] > max) {
                max = values[i];
            }
        }

        return max;
    }

    public static void main(String[] args) {
        long[] values = {3, 4, 9, 12};
        Accumulator accumulator = new Sum();
        for (long value : values) {
            accumulator.add(value);
        }
        Result result = new Result(values.length, accumulator.total());
        List<Result> history = new ArrayList<>();
        history.add(result);
        System.out.println("MAX=" + Max(values));
        System.out.println("sum=" + result.sum() + ", average=" + result.average());
        System.out.println("history size=" + history.size());
        System.out.println("Java=" + System.getProperty("java.version"));
        System.out.println("Java home=" + System.getProperty("java.home"));
        System.out.println("OS=" + System.getProperty("os.name"));
        System.out.println("architecture=" + System.getProperty("os.arch"));
        System.out.println("available processors=" + Runtime.getRuntime().availableProcessors());
    }
}
