
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Основной класс, представляющий реактивный поток данных.
 * @param <T> Тип данных в потоке
 */
public class Observable<T> {

    private final ObservableOnSubscribe<T> source;

    private Observable(ObservableOnSubscribe<T> source) {
        this.source = source;
    }

    /**
     * Создает новый Observable из источника.
     */
    public static <T> Observable<T> create(ObservableOnSubscribe<T> source) {
        return new Observable<>(source);
    }

    /**
     * Подписывает Observer на получение данных от Observable.
     */
    public Disposable subscribe(Observer<T> observer) {
        SafeEmitter<T> safeEmitter = new SafeEmitter<>(observer);
        
        // Создаем Disposable для управления подпиской
        Disposable disposable = new Disposable() {
            @Override
            public void dispose() {
                safeEmitter.disposeResources();
            }
            
            @Override
            public boolean isDisposed() {
                return safeEmitter.isDisposed();
            }
        };
        
        // Устанавливаем Disposable в эмиттер
        safeEmitter.setDisposable(disposable);
        
        try {
            source.subscribe(safeEmitter);
        } catch (Throwable t) {
            safeEmitter.onError(t);
        }
        
        return disposable;
    }

    /**
     * Оператор преобразования данных.
     */
    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        return new Observable<>(emitter -> this.subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                emitter.setDisposable(d);
            }

            @Override
            public void onNext(T t) {
                try {
                    R result = mapper.apply(t);
                    emitter.onNext(result);
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                emitter.onError(e);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    /**
     * Оператор фильтрации данных.
     */
    public Observable<T> filter(Predicate<? super T> predicate) {
        return new Observable<>(emitter -> subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                emitter.setDisposable(d);
            }

            @Override
            public void onNext(T t) {
                try {
                    if (predicate.test(t)) {
                        emitter.onNext(t);
                    }
                } catch (Throwable e) {
                    emitter.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                emitter.onError(e);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    /**
     * Оператор, который преобразует каждый элемент в Observable, а затем "сглаживает"
     * эти Observable в единый поток.
     */
    public <R> Observable<R> flatMap(Function<? super T, ? extends Observable<? extends R>> mapper) {
        return new Observable<>(emitter -> {
            CompositeDisposable composite = new CompositeDisposable();
            
            // Устанавливаем составной Disposable для эмиттера
            emitter.setDisposable(composite);
            
            AtomicInteger wip = new AtomicInteger(1);
            AtomicBoolean done = new AtomicBoolean();
            AtomicReference<Throwable> error = new AtomicReference<>();
            
            Disposable mainDisposable = this.subscribe(
                new Observer<T>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        composite.add(d);
                    }
                    
                    @Override
                    public void onNext(T item) {
                        if (composite.isDisposed()) return;
                        
                        try {
                            Observable<? extends R> innerObservable = mapper.apply(item);
                            wip.getAndIncrement();
                            
                            Disposable innerDisposable = innerObservable.subscribe(
                                new Observer<R>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {
                                        composite.add(d);
                                    }
                                    
                                    @Override
                                    public void onNext(R r) {
                                        if (!composite.isDisposed()) {
                                            emitter.onNext(r);
                                        }
                                    }
                                    
                                    @Override
                                    public void onError(Throwable t) {
                                        onInnerError(t);
                                    }
                                    
                                    @Override
                                    public void onComplete() {
                                        if (wip.decrementAndGet() == 0) {
                                            checkTermination();
                                        }
                                    }
                                    
                                    private void onInnerError(Throwable t) {
                                        if (error.compareAndSet(null, t)) {
                                            composite.dispose();
                                            emitter.onError(t);
                                        }
                                    }
                                }
                            );
                        } catch (Throwable t) {
                            if (!composite.isDisposed()) {
                                onError(t);
                            }
                        }
                    }
                    
                    @Override
                    public void onError(Throwable t) {
                        if (error.compareAndSet(null, t)) {
                            composite.dispose();
                            emitter.onError(t);
                        }
                    }
                    
                    @Override
                    public void onComplete() {
                        done.set(true);
                        if (wip.decrementAndGet() == 0) {
                            checkTermination();
                        }
                    }
                    
                    private void checkTermination() {
                        if (done.get() && wip.get() == 0) {
                            Throwable ex = error.get();
                            if (ex != null) {
                                emitter.onError(ex);
                            } else {
                                emitter.onComplete();
                            }
                        }
                    }
                }
            );
            
            composite.add(mainDisposable);
        });
    }
    
    private void checkTermination(Emitter<?> emitter, Throwable error) {
        if (error != null) {
            emitter.onError(error);
        } else {
            emitter.onComplete();
        }
    }


    public Observable<T> subscribeOn(Scheduler scheduler) {
        return new Observable<>(emitter -> 
            scheduler.execute(() -> 
                this.subscribe(new Observer<T>() {
                    @Override public void onNext(T item) { emitter.onNext(item); }
                    @Override public void onError(Throwable t) { emitter.onError(t); }
                    @Override public void onComplete() { emitter.onComplete();}
                    @Override public void onSubscribe(Disposable d) {emitter.setDisposable(d);} 
                }))
            );
    }

    /**
     * Указывает Scheduler, в котором будет выполняться подписка (метод create).
     */
    public Observable<T> observeOn(Scheduler scheduler) {
        return new Observable<>(emitter -> {
            final Queue<T> queue = new ConcurrentLinkedQueue<>();
            final AtomicBoolean isProcessing = new AtomicBoolean(false);
            final AtomicBoolean completed = new AtomicBoolean(false);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>();
            
            this.subscribe(new Observer<T>() {
                @Override
                public void onSubscribe(Disposable d) {
                    // emitted.setDisposable(d);
                }

                @Override
                public void onNext(T item) {
                    queue.offer(item);
                    scheduleDrain(scheduler, emitter, queue, isProcessing, completed, errorRef);
                }
                
                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                    completed.set(true);
                    scheduleDrain(scheduler, emitter, queue, isProcessing, completed, errorRef);
                }
                
                @Override
                public void onComplete() {
                    completed.set(true);
                    scheduleDrain(scheduler, emitter, queue, isProcessing, completed, errorRef);
                }
            });
        });
    }
    
    private void scheduleDrain(Scheduler scheduler, 
                               Emitter<T> emitter, 
                               Queue<T> queue, 
                               AtomicBoolean isProcessing, 
                               AtomicBoolean completed,
                               AtomicReference<Throwable> errorRef) {
        if (isProcessing.compareAndSet(false, true)) {
            scheduler.execute(() -> {
                try {
                    // Обрабатываем все элементы в очереди
                    while (!queue.isEmpty()) {
                        emitter.onNext(queue.poll());
                    }
                    
                    // Проверяем завершение
                    if (completed.get()) {
                        Throwable error = errorRef.get();
                        if (error != null) {
                            emitter.onError(error);
                        } else {
                            emitter.onComplete();
                        }
                    }
                } finally {
                    isProcessing.set(false);
                    // Проверяем, не появились ли новые события
                    if (!queue.isEmpty() || (completed.get() && !isProcessing.get())) {
                        scheduleDrain(scheduler, emitter, queue, isProcessing, completed, errorRef);
                    }
                }
            });
        }
    }

    // Внутренний класс для безопасной эмиссии событий
    private static class SafeEmitter<T> implements Emitter<T> {
        private final Observer<T> observer;
        private volatile boolean isDisposed = false;
        private volatile boolean isCompleted = false;
        private Disposable currentDisposable;

        SafeEmitter(Observer<T> observer) {
            this.observer = observer;
        }

        @Override
        public void setDisposable(Disposable d) {
            this.currentDisposable = d;
            // Уведомляем подписчика о создании подписки
            if (!isDisposed && !isCompleted) {
                try {
                    observer.onSubscribe(d);
                } catch (Throwable e) {
                    onError(e);
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return isDisposed;
        }
        @Override
        public void onNext(T item) {
            if (isDisposed || isCompleted) return;
            try {
                observer.onNext(item);
            } catch (Throwable t) {
                onError(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (isDisposed || isCompleted) return;
            isCompleted = true;
            try {
                observer.onError(t);
            } catch (Throwable e) {
                e.addSuppressed(t);
                Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), e);
            } finally {
                disposeResources();
            }
        }

        @Override
        public void onComplete() {
            if (isDisposed || isCompleted) return;
            isCompleted = true;
            try {
                observer.onComplete();
            } catch (Throwable t) {
                Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), t);
            } finally {
                disposeResources();
            }
        }


         private void disposeResources() {
            isDisposed = true;
            if (currentDisposable != null) {
                currentDisposable.dispose();
            }
        }


    }


    //     // Внутренний класс для простого управления подпиской
//     private static class BasicDisposable implements Disposable {
//         private volatile boolean disposed = false;

//         @Override
//         public void dispose() {
//             disposed = true;
//         }

//         @Override
//         public boolean isDisposed() {
//             return disposed;
//         }
//     }
    public static class CompositeDisposable implements Disposable {
        private final List<Disposable> disposables = new CopyOnWriteArrayList<>();
        private volatile boolean disposed;

        public void add(Disposable disposable) {
            if (disposed) {
                disposable.dispose();
            } else {
                disposables.add(disposable);
            }
        }

        @Override
        public void dispose() {
            if (!disposed) {
                disposed = true;
                for (Disposable d : disposables) {
                    d.dispose();
                }
                disposables.clear();
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
