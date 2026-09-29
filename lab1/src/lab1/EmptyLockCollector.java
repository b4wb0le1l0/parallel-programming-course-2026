package lab1;

// Только эксперимент стоимости замка: этот вариант намеренно НЕ хранит записи.
public final class EmptyLockCollector implements MetricsCollector {
    @Override
    public synchronized void record(long value) {
        // Взять замок объекта и сразу отпустить.
    }

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);
    }
}
