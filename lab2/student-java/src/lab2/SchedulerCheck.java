package lab2;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class SchedulerCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Main.Outcome run(String target, Strategy strategy) {
        return Main.runOnce(Targets.byName(target), strategy, false);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an exception");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            check(expected.getMessage() != null, "Missing error message");
        }
    }

    public static void main(String[] args) {
        check(run("withdraw-v0", new Strategies.FirstAlive()).trace().equals("AAAB"), "V0 trace");
        check(run("withdraw-v1", new Strategies.FirstAlive()).trace().equals("AAAABB"), "V1 trace");
        check(run("transfer", new Strategies.FirstAlive()).trace().equals("AAAABBBB"), "Transfer trace");
        Main.Outcome dead = run("transfer", new Strategies.Replay("AB"));
        check(dead.trace().equals("AB"), "Deadlock trace");
        check(dead.failure().equals("DEADLOCK held={a:A, b:B}"), "Deadlock owners");
        expectFailure(() -> run("withdraw-v1", new Strategies.Replay("AABB")));
        expectFailure(() -> run("withdraw-v1", new Strategies.Replay("AB")));
        expectFailure(() -> run("withdraw-v1", new Strategies.Replay("")));
        Main.Outcome extra = run("transfer", new Strategies.Replay("AAAABBBBZZ"));
        check(extra.failure() == null, "Extra replay suffix should be ignored");
        Main.Outcome random = run("withdraw-v1", new Strategies.RandomPick(17));
        check(random.equals(run("withdraw-v1", new Strategies.RandomPick(17))), "Seed is not repeatable");
        check(random.equals(run("withdraw-v1", new Strategies.Replay(random.trace()))), "Replay mismatch");

        Strategies.Dfs dfs = new Strategies.Dfs(2);
        Set<String> traces = new HashSet<>();
        do {
            Main.Outcome outcome = run("collector-ok-2x1", dfs);
            check(outcome.failure() == null, "Correct collector failed");
            check(traces.add(outcome.trace()), "DFS repeated a trace");
        } while (dfs.endRun());
        check(traces.size() == 1334, "Expected 1334 schedules, got " + traces.size());

        // At bound zero, a switch from a finished or blocked worker is still allowed.
        Strategies.Dfs zero = new Strategies.Dfs(0);
        zero.beginRun();
        check(zero.pick(List.of(0, 1), -1) == 0, "Initial choice");
        check(zero.pick(List.of(0, 1), 0) == 0, "Unexpected preemption");
        check(zero.pick(List.of(1), 0) == 1, "Forced switch was counted as preemption");

        Scheduler empty = new Scheduler();
        empty.spawn(() -> {});
        empty.run(new Strategies.FirstAlive());
        check(empty.traceString().isEmpty(), "Empty worker needs no decision");
        expectFailure(() -> empty.run(new Strategies.FirstAlive()));

        Scheduler waiting = new Scheduler();
        Shared<Boolean> done = new Shared<>("done", false);
        waiting.spawn(() -> Scheduler.await(done::peek));
        waiting.spawn(() -> done.set(true));
        waiting.run(new Strategies.FirstAlive());
        check(waiting.traceString().equals("BA"), "Await must disable the waiting worker");
        check(waiting.deadlock == null, "Await did not resume");

        Scheduler windows = new Scheduler();
        Shared<Integer> number = new Shared<>("number", 0);
        windows.spawn(() -> {
            Scheduler.point();
            Scheduler.point();
            number.set(1);
            Scheduler.point();
        });
        windows.run(new Strategies.FirstAlive());
        check(windows.traceString().equals("A"), "Windows must not add their own pauses");
        check(number.peek() == 1, "Shared.set did not store the value");

        Scheduler stuck = new Scheduler();
        stuck.spawn(() -> Scheduler.await(() -> false));
        stuck.run(new Strategies.FirstAlive());
        check("held={}".equals(stuck.deadlock), "Condition deadlock");

        Scheduler broken = new Scheduler();
        broken.spawn(() -> { throw new IllegalStateException("Target failed"); });
        expectFailure(() -> broken.run(new Strategies.FirstAlive()));
        Scheduler invalidUnlock = new Scheduler();
        SchedLock lock = new SchedLock(invalidUnlock, "test");
        invalidUnlock.spawn(lock::unlock);
        expectFailure(() -> invalidUnlock.run(new Strategies.FirstAlive()));
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            check(!thread.getName().startsWith("scheduled-"), "Worker leaked: " + thread.getName());
        }
        System.out.println("All scheduler checks passed");
    }
}
