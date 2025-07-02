/**
 * Интерфейс планировщика для управления потоками выполнения.
 */
public interface Scheduler {
    /**
     * Выполняет задачу.s
     */
    void execute(Runnable task);

    void shutdown();
}