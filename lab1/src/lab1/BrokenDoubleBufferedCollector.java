package lab1;

// Только эксперимент этапа 4: повторная проверка active намеренно отключена.
public final class BrokenDoubleBufferedCollector extends DoubleBufferedCollector {
    public BrokenDoubleBufferedCollector() {
        super(false);
    }
}
