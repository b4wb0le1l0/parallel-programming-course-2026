package practice;

import java.util.concurrent.CountDownLatch;

public class S07StartGate {
    public static void main(String[] args) throws InterruptedException {
        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[workers];
        long[] results = new long[workers];

        for (int k = 0; k < workers; k++) {
            final int id = k;
            threads[k] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long local = 0;
                for (int i = 0; i < 10; i++) {
                    local++;
                }
                results[id] = local;
            });
            threads[k].start();
        }
        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        long total = 0;
        for (long result : results) {
            total += result;
        }
        System.out.println("expected=400000, actual=" + total);
    }
}
