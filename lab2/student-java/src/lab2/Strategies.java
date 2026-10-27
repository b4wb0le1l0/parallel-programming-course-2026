package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Strategies {
    private Strategies() {}

    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) { return enabled.get(0); }
    }

    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) { this.trace = trace; }
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) {
                throw new IllegalArgumentException("Replay trace ended at decision " + (position + 1));
            }
            int next = trace.charAt(position++) - 'A';
            if (!enabled.contains(next)) {
                throw new IllegalArgumentException("Replay selected disabled worker at decision " + position);
            }
            return next;
        }
    }

    public static final class RandomPick implements Strategy {
        private final Random random;
        public RandomPick(long seed) { random = new Random(seed); }
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(random.nextInt(enabled.size()));
        }
    }

    public static final class Dfs implements Strategy {
        private static final class Choice {
            final List<Integer> options;
            int selected;
            Choice(List<Integer> options) { this.options = options; }
        }

        private final int bound;
        private final List<Choice> path = new ArrayList<>();
        private int position;
        private int preemptions;

        public Dfs(int bound) {
            if (bound < 0) throw new IllegalArgumentException("Bound must be non-negative");
            this.bound = bound;
        }

        public void beginRun() {
            position = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (position == path.size()) {
                List<Integer> options = new ArrayList<>();
                for (int next : enabled) {
                    boolean preemption = next != last && enabled.contains(last);
                    if (!preemption || preemptions < bound) options.add(next);
                }
                path.add(new Choice(options));
            }
            Choice choice = path.get(position++);
            int next = choice.options.get(choice.selected);
            if (next != last && enabled.contains(last)) preemptions++;
            return next;
        }

        public boolean endRun() {
            // Remove the exhausted suffix, then advance the nearest remaining choice.
            while (!path.isEmpty()) {
                Choice choice = path.get(path.size() - 1);
                if (choice.selected + 1 < choice.options.size()) {
                    choice.selected++;
                    return true;
                }
                path.remove(path.size() - 1);
            }
            return false;
        }
    }
}
