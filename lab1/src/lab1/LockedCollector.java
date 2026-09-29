package lab1;

// Этап 1: все обращения к одному внутреннему коллектору идут под одним замком.
public final class LockedCollector implements MetricsCollector {
    private final SequentialCollector data = new SequentialCollector();

    @Override
    public synchronized void record(long value) {
        data.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return data.snapshot();
    }
}
