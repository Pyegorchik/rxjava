
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
        AtomicBoolean disposed = new AtomicBoolean(false);
        SafeEmitter<T> safeEmitter = new SafeEmitter<>(observer);
        try {
            source.subscribe(safeEmitter);
        } catch (Throwable t) {
            safeEmitter.onError(t);
        }
    }

    /**
     * Оператор преобразования данных.
     */
    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        return new Observable<>(emitter -> this.subscribe(new Observer<T>() {
            // @Override
            // public void onSubscribe(Disposable d) {
            //     observer.onSubscribe(d);
            // }

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
            // @Override
            // public void onSubscribe(Disposable d) {
            //     observer.onSubscribe(d);
            // }

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

    // /**
    //  * Оператор, который преобразует каждый элемент в Observable, а затем "сглаживает"
    //  * эти Observable в единый поток.
    //  */
    // public <R> Observable<R> flatMap(Function<T, ? extends Observable<R>> mapper) {
    //     return create(downstreamObserver -> subscribe(new Observer<T>() {
    //         private Disposable upstreamDisposable;
    //         private final AtomicReference<Disposable> innerDisposable = new AtomicReference<>();
    //         private volatile boolean done;

    //         @Override
    //         public void onSubscribe(Disposable d) {
    //             this.upstreamDisposable = d;
    //             downstreamObserver.onSubscribe(d);
    //         }

    //         @Override
    //         public void onNext(T t) {
    //             try {
    //                 Observable<? extends R> innerObservable = mapper.apply(t);
    //                 innerObservable.subscribe(new Observer<R>() {
    //                     @Override
    //                     public void onSubscribe(Disposable d) {
    //                        // Можно управлять внутренними подписками
    //                        innerDisposable.set(d);
    //                     }

    //                     @Override
    //                     public void onNext(R r) {
    //                         downstreamObserver.onNext(r);
    //                     }

    //                     @Override
    //                     public void onError(Throwable e) {
    //                         downstreamObserver.onError(e);
    //                     }

    //                     @Override
    //                     public void onComplete() {
    //                        // Внутренний поток завершился
    //                     }
    //                 });
    //             } catch (Exception e) {
    //                 downstreamObserver.onError(e);
    //             }
    //         }

    //         @Override
    //         public void onError(Throwable e) {
    //             downstreamObserver.onError(e);
    //         }

    //         @Override
    //         public void onComplete() {
    //             done = true;
    //             // Завершаем основной поток только когда все внутренние потоки завершены
    //             // Для простоты, здесь мы завершаем сразу.
    //             downstreamObserver.onComplete();
    //         }
    //     }));
    // }


    public Observable<T> subscribeOn(Scheduler scheduler) {
        return new Observable<>(emitter -> 
            scheduler.execute(() -> 
                this.subscribe(new Observer<T>() {
                    @Override public void onNext(T item) { emitter.onNext(item); }
                    @Override public void onError(Throwable t) { emitter.onError(t); }
                    @Override public void onComplete() { emitter.onComplete(); }
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
        private boolean isCompleted = false;

        SafeEmitter(Observer<T> observer) {
            this.observer = observer;
        }

        @Override
        public void onNext(T item) {
            if (!isCompleted) {
                try {
                    observer.onNext(item);
                } catch (Throwable t) {
                    onError(t);
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (!isCompleted) {
                isCompleted = true;
                try {
                    observer.onError(t);
                } catch (Throwable e) {
                    // Обработка ошибки в подписчике
                    e.addSuppressed(t);
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onComplete() {
            if (!isCompleted) {
                isCompleted = true;
                try {
                    observer.onComplete();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
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

}
