/**
 * Интерфейс планировщика для управления потоками выполнения.
 */
public interface Scheduler {
    /**
     * Выполняет задачу.
     * @param task задача для выполнения
     */
    void execute(Runnable task);
}