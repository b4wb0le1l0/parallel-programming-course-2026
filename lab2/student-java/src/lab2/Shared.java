package lab2;

public final class Shared<T> {
    private final String name;
    private T value;

    public Shared(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T get() {
        Scheduler.before(() -> true);
        Scheduler.record(name + " -> " + value);
        return value;
    }

    public void set(T value) {
        Scheduler.before(() -> true);
        this.value = value;
        Scheduler.record(name + " = " + value);
    }

    // These methods model step order, not CPU memory ordering.
    public T getAcquire() { return get(); }
    public void setRelease(T value) { set(value); }
    public T peek() { return value; }
}
