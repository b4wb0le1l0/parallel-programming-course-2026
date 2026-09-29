package practice;

import java.util.concurrent.CountDownLatch;

public class S07Door {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            System.out.println("Рабочий: жду разрешения");
            try {
                start.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println("Рабочий: выполняю задачу");
        });

        worker.start();
        System.out.println("Главный: разрешаю работать");
        start.countDown();
        worker.join();
        System.out.println("Главный: рабочий закончил");
    }
}
