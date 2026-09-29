package practice;

public class InterfaceDemo {
    interface Sender {
        void send(String text);
    }

    static class ConsoleSender implements Sender {
        @Override
        public void send(String text) {
            System.out.println(text);
        }
    }

    static void greet(Sender sender) {
        sender.send("Привет!");
    }

    public static void main(String[] args) {
        Sender sender = new ConsoleSender();
        greet(sender);
    }
}