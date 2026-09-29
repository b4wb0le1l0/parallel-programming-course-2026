package practice;

public class S03Race {
    private static int counter;

    static void incrementMany() {
        for (int i = 0; i < 1_000_000; i++) {
            counter++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            counter = 0;
            Thread a = new Thread(S03Race::incrementMany);
            Thread b = new Thread(S03Race::incrementMany);
            a.start();
            b.start();
            a.join();
            b.join();
            System.out.println("expected=2000000, actual=" + counter);
        }
    }
}
