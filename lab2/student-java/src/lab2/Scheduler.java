package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public final class Scheduler {
    boolean logging;
    final List<String> log = new ArrayList<>();
    String deadlock;

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();
    private final List<Worker> workers = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore returned = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private boolean stopped;
    private boolean started;

    private static final class Cancelled extends RuntimeException {
        Cancelled() { super(null, null, false, false); }
    }

    private static final class Worker {
        final Scheduler scheduler;
        final int id;
        final Runnable body;
        final Semaphore turn = new Semaphore(0);
        Thread thread;
        BooleanSupplier ready = () -> true;
        boolean touched = true;
        boolean finished;
        Throwable failure;

        Worker(Scheduler scheduler, int id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.body = body;
        }
    }

    public void spawn(Runnable body) {
        if (started) throw new IllegalStateException("Cannot spawn after run starts");
        workers.add(new Worker(this, workers.size(), body));
    }

    public void run(Strategy strategy) {
        if (started) throw new IllegalStateException("Use a new scheduler for each run");
        started = true;
        try {
            // Start each worker up to its first pause, without running workers together.
            for (Worker worker : workers) {
                worker.thread = new Thread(() -> execute(worker), "scheduled-" + worker.id);
                worker.thread.start();
                returned.acquireUninterruptibly();
                checkFailures();
            }
            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean allFinished = true;
                for (Worker worker : workers) {
                    if (!worker.finished) {
                        allFinished = false;
                        if (worker.ready.getAsBoolean()) enabled.add(worker.id);
                    }
                }
                if (allFinished) break;
                if (enabled.isEmpty()) {
                    deadlock = heldLocks();
                    break;
                }
                int next = strategy.pick(enabled, last);
                if (!enabled.contains(next)) {
                    throw new IllegalArgumentException("Strategy selected a disabled worker: " + next);
                }
                trace.append((char) ('A' + next));
                last = next;
                workers.get(next).turn.release();
                returned.acquireUninterruptibly();
                checkFailures();
            }
        } finally {
            // Cancel parked workers even when Replay or a target throws.
            stopped = true;
            for (Worker worker : workers) worker.turn.release();
            boolean interrupted = false;
            for (Worker worker : workers) {
                if (worker.thread == null) continue;
                while (worker.thread.isAlive()) {
                    try {
                        worker.thread.join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void execute(Worker worker) {
        CURRENT.set(worker);
        try {
            worker.body.run();
        } catch (Cancelled ignored) {
            // Cancellation is only used to unwind a parked worker.
        } catch (Throwable error) {
            worker.failure = error;
        } finally {
            worker.finished = true;
            CURRENT.remove();
            returned.release();
        }
    }

    private void checkFailures() {
        for (Worker worker : workers) {
            if (worker.failure != null) {
                throw new IllegalStateException("Worker " + (char) ('A' + worker.id)
                        + " failed", worker.failure);
            }
        }
    }

    private String heldLocks() {
        StringBuilder result = new StringBuilder("held={");
        boolean first = true;
        for (SchedLock lock : locks) {
            if (lock.owner < 0) continue;
            if (!first) result.append(", ");
            result.append(lock.name).append(":").append((char) ('A' + lock.owner));
            first = false;
        }
        return result.append("}").toString();
    }

    private static Worker current() {
        Worker worker = CURRENT.get();
        if (worker == null) throw new IllegalStateException("Operation outside a scheduled worker");
        return worker;
    }

    static Scheduler currentScheduler() { return current().scheduler; }
    static int currentId() { return current().id; }

    static void before(BooleanSupplier ready) {
        Worker worker = current();
        Scheduler scheduler = worker.scheduler;
        if (scheduler.stopped) throw new Cancelled();
        if (worker.touched || !ready.getAsBoolean()) {
            worker.ready = ready;
            worker.touched = false;
            scheduler.returned.release();
            worker.turn.acquireUninterruptibly();
            if (scheduler.stopped) throw new Cancelled();
        }
        // The controller only resumes a worker whose condition is true.
        worker.ready = () -> true;
        worker.touched = true;
    }

    static void record(String operation) {
        Worker worker = current();
        if (worker.scheduler.stopped) return;
        if (worker.scheduler.logging) {
            worker.scheduler.log.add((char) ('A' + worker.id) + "  " + operation);
        }
    }

    boolean isStopped() { return stopped; }

    public String traceString() { return trace.toString(); }

    public static void point() {
        current().touched = true;
        record("window");
    }

    public static void await(BooleanSupplier condition) {
        before(condition);
        record("await done");
    }
}
