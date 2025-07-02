import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Планировщик, использующий один поток для выполнения задач.
 */
public class SingleThreadScheduler implements Scheduler {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}
