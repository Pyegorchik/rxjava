public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Демонстрация map и filter ---");
        demoMapAndFilter();

        System.out.println("\n--- Демонстрация Schedulers (subscribeOn, observeOn) ---");
        demoSchedulers();
        
        // System.out.println("\n--- Демонстрация flatMap ---");
        demoFlatMap();
        
        System.out.println("\n--- Демонстрация обработки ошибок ---");
        demoErrorHandling();
        
        // Даем время асинхронным операциям завершиться
        Thread.sleep(2000); 
        System.exit(0);
    }

    private static void demoMapAndFilter() {
        Observable.create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onNext(4);
            emitter.onNext(5);
            emitter.onComplete();
        })
        .filter(i -> ((Integer) i) % 2 != 0) // Фильтруем нечетные: 1, 3, 5
        .map(i -> "Число: " + i) // Преобразуем в строку
        .subscribe(new DefaultObserver<>("MapFilter"));
    }
    
    private static void demoSchedulers() {
        Scheduler ioScheduler = new IOThreadScheduler();
        Scheduler computationScheduler = new ComputationScheduler();

        System.out.println("Старт из потока: " + Thread.currentThread().getName());
        
        Observable.create(emitter -> {
            System.out.println("Код create выполняется в потоке: " + Thread.currentThread().getName());
            emitter.onNext(1);
            emitter.onNext(2);
            // emitter.onError(new RuntimeException("Искусственная ошибка"));
            emitter.onNext(3); // Не должно быть доставлено
            emitter.onComplete();
        })
        .map(i -> {
            System.out.println("Оператор map выполняется в потоке: " + Thread.currentThread().getName());
            return ((Integer)i) * 10;
        })
        .subscribeOn(ioScheduler) // Подписка будет в IO потоке
        .observeOn(computationScheduler) // Обработка результата - в Computation потоке
        .subscribe(new DefaultObserver<Integer>("Schedulers"));
    }
    
    // private static void demoFlatMap() {
    //     Observable.create(emitter -> {
    //         emitter.onNext("A");
    //         emitter.onNext("B");
    //         emitter.onComplete();
    //     })
    //     .flatMap(prefix -> Observable.create(emitter -> {
    //         emitter.onNext(prefix + "1");
    //         emitter.onNext(prefix + "2");
    //         emitter.onComplete();
    //     }))
    //     .subscribe(new DefaultObserver<>("FlatMap"));
    // }
    
    private static void demoErrorHandling() {
        Observable.create(emitter -> {
            emitter.onNext(10);
            emitter.onNext(20);
            // Имитируем ошибку
            emitter.onError(new RuntimeException("Что-то пошло не так!"));
            emitter.onNext(30); // Это значение не будет отправлено
        })
        .<Integer>map(i -> ((Integer)i) / 2)
        .subscribe(new DefaultObserver<>("ErrorHandling"));
    }
}

// Вспомогательный класс для демонстрации
class DefaultObserver<T> implements Observer<T> {
    private final String name;
    private Disposable disposable;

    public DefaultObserver(String name) {
        this.name = name;
    }

    @Override
    public void onSubscribe(Disposable d) {
        this.disposable = d;
        System.out.printf("[%s] onSubscribe в потоке: %s\n", name, Thread.currentThread().getName());
    }

    @Override
    public void onNext(T t) {
        System.out.printf("[%s] onNext: %s в потоке: %s\n", name, t, Thread.currentThread().getName());
    }

    @Override
    public void onError(Throwable e) {
        System.err.printf("[%s] onError: %s в потоке: %s\n", name, e.getMessage(), Thread.currentThread().getName());
    }

    @Override
    public void onComplete() {
        System.out.printf("[%s] onComplete в потоке: %s\n", name, Thread.currentThread().getName());
    }
}