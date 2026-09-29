package practice;

public class S04Monitor {
    static final class Counter {
        private final Object lock = new Object();
        private long value;
        private long sum;

        synchronized void increment() {
            value++;
        }

        synchronized long get() {
            return value;
        }

        synchronized void add(long number) {
            value++;
            sum += number;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Runnable inc = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.increment();
            }
        };

        Runnable inc2 = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.add(i);
            }
        };

        Thread a = new Thread(inc);
        Thread b = new Thread(inc2);
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("expected=200000, actual=" + counter.get());
        System.out.println("expected=19999900000, actual=" + counter.sum);
    }
}
