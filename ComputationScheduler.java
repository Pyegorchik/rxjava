import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик для вычислительных задач. Использует пул потоков фиксированного размера.
 */
public class ComputationScheduler implements Scheduler {
    private final int coreCount = Runtime.getRuntime().availableProcessors();
    private final ExecutorService executor = Executors.newFixedThreadPool(coreCount);

    

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
