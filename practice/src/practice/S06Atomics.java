package practice;

import java.util.concurrent.atomic.AtomicLong;

public class S06Atomics {
    private static final AtomicLong count = new AtomicLong();
    private static final AtomicLong maximum = new AtomicLong(Long.MIN_VALUE);

    static void offerMaximum(long candidate) {
        while (true) {
            long old = maximum.get();
            if (candidate <= old) {
                return;
            }
            if (maximum.compareAndSet(old, candidate)) {
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[4];
        for (int k = 0; k < threads.length; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                for (int i = 0; i < 10_000; i++) {
                    count.incrementAndGet();
                    offerMaximum(id * 10_000L + i);
                }
            });
            threads[k].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("expected count=40000, actual=" + count.get());
        System.out.println("expected max=39999, actual=" + maximum.get());
    }
}
