package practice;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class S09SplitSnapshot {
    public static void main(String[] args) throws InterruptedException {
        AtomicLong left = new AtomicLong();
        AtomicLong right = new AtomicLong();
        CountDownLatch halfway = new CountDownLatch(1);
        CountDownLatch continueWriting = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            left.incrementAndGet();
            halfway.countDown();
            try {
                continueWriting.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            right.incrementAndGet();
        });
        writer.start();
        halfway.await();
        System.out.println("During update: left=" + left.get() + ", right=" + right.get());
        continueWriting.countDown();
        writer.join();
        System.out.println("After join: left=" + left.get() + ", right=" + right.get());
    }
}
