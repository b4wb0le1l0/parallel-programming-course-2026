package lab1;

// Этап 4 эксперимент: повторная проверка active отключена.
public final class BrokenDoubleBufferedCollector extends DoubleBufferedCollector {
    public BrokenDoubleBufferedCollector() {
        super(false);
    }
}
