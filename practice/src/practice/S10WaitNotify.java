package practice;

public class S10WaitNotify {
    static final class Mailbox {
        private String message;

        synchronized void put(String value) {
            message = value;
            notifyAll();
        }

        synchronized String take() throws InterruptedException {
            while (message == null) {
                wait();
            }
            String result = message;
            message = null;
            return result;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Mailbox mailbox = new Mailbox();
        Thread reader = new Thread(() -> {
            try {
                System.out.println("Received: " + mailbox.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        reader.start();
        mailbox.put("hello");
        reader.join();
    }
}
