package practice;

import java.util.ArrayList;
import java.util.List;

public class S08ThreadLocal {
    static final class State {
        long count;
    }

    public static void main(String[] args) throws InterruptedException {
        Object listLock = new Object();
        List<State> states = new ArrayList<>();
        ThreadLocal<State> local = ThreadLocal.withInitial(() -> {
            State state = new State();
            synchronized (listLock) {
                states.add(state);
            }
            return state;
        });
        Thread[] threads = new Thread[4];
        for (int k = 0; k < threads.length; k++) {
            threads[k] = new Thread(() -> {
                State mine = local.get();
                for (int i = 0; i < 100_000; i++) {
                    mine.count++;
                }
                local.remove();
            });
            threads[k].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long total = 0;
        synchronized (listLock) {
            for (State state : states) {
                total += state.count;
            }
        }
        System.out.println("states=" + states.size());
        System.out.println("expected=400000, actual=" + total);
    }
}
