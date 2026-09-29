package practice;

public class S05StopFlag {
    private static volatile boolean stop;
    private static long result;

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long local = 0;
            while (!stop) {
                local++;
            }
            result = local;
        });
        worker.start();
        Thread.sleep(200);
        stop = true;
        worker.join();
        System.out.println("Stopped; iterations=" + result);
    }
}
