package practice;

public class S02Threads {
    static void printSteps() {
        for (int i = 0; i < 5; i++) {
            System.out.println(Thread.currentThread().getName() + ": " + i);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Runnable task = S02Threads::printSteps;
        Thread first = new Thread(task, "first");
        Thread second = new Thread(() -> printSteps(), "second");
        Thread third = new Thread(() -> printSteps(), "third");
        first.start();
        second.start();
        third.start();
        first.join();
        second.join();
        third.join();
        System.out.println("All finished; current=" + Thread.currentThread().getName());
    }
}
