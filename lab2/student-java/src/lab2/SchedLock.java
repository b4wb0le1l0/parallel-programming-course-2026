package lab2;

public final class SchedLock {
    final String name;
    final Scheduler scheduler;
    int owner = -1;

    public SchedLock(Scheduler scheduler, String name) {
        this.scheduler = scheduler;
        this.name = name;
        scheduler.locks.add(this);
    }

    private void checkScheduler() {
        if (Scheduler.currentScheduler() != scheduler) {
            throw new IllegalStateException("Lock belongs to another scheduler");
        }
    }

    public void lock() {
        checkScheduler();
        Scheduler.before(() -> owner == -1);
        owner = Scheduler.currentId();
        Scheduler.record("lock " + name);
    }

    public void unlock() {
        if (scheduler.isStopped()) return;
        checkScheduler();
        Scheduler.before(() -> true);
        if (owner != Scheduler.currentId()) {
            throw new IllegalStateException("Cannot unlock another worker's lock: " + name);
        }
        owner = -1;
        Scheduler.record("unlock " + name);
    }
}
