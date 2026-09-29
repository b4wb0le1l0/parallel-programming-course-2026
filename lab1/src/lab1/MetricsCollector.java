package lab1;

public interface MetricsCollector {
    void record(long value);
    Snapshot snapshot();
}
