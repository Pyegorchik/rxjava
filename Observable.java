
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
    public void subscribe(Observer<T> observer) {
        // Создаем простой Disposable для примера
        BasicDisposable disposable = new BasicDisposable();
        observer.onSubscribe(disposable);
        
        if (disposable.isDisposed()) {
            return;
        }

        try {
            source.subscribe(observer);
        } catch (Throwable t) {
            if (!disposable.isDisposed()) {
                observer.onError(t);
            }
        }
    }

    /**
     * Оператор преобразования данных.
     */
    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        return create(observer -> subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                observer.onSubscribe(d);
            }

            @Override
            public void onNext(T t) {
                try {
                    R result = mapper.apply(t);
                    observer.onNext(result);
                } catch (Exception e) {
                    observer.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        }));
    }

    /**
     * Оператор фильтрации данных.
     */
    public Observable<T> filter(Predicate<? super T> predicate) {
        return create(observer -> subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                observer.onSubscribe(d);
            }

            @Override
            public void onNext(T t) {
                try {
                    if (predicate.test(t)) {
                        observer.onNext(t);
                    }
                } catch (Exception e) {
                    observer.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        }));
    }

    /**
     * Оператор, который преобразует каждый элемент в Observable, а затем "сглаживает"
     * эти Observable в единый поток.
     */
    public <R> Observable<R> flatMap(Function<T, ? extends Observable<R>> mapper) {
        return create(downstreamObserver -> subscribe(new Observer<T>() {
            private Disposable upstreamDisposable;
            private final AtomicReference<Disposable> innerDisposable = new AtomicReference<>();
            private volatile boolean done;

            @Override
            public void onSubscribe(Disposable d) {
                this.upstreamDisposable = d;
                downstreamObserver.onSubscribe(d);
            }

            @Override
            public void onNext(T t) {
                try {
                    Observable<? extends R> innerObservable = mapper.apply(t);
                    innerObservable.subscribe(new Observer<R>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                           // Можно управлять внутренними подписками
                           innerDisposable.set(d);
                        }

                        @Override
                        public void onNext(R r) {
                            downstreamObserver.onNext(r);
                        }

                        @Override
                        public void onError(Throwable e) {
                            downstreamObserver.onError(e);
                        }

                        @Override
                        public void onComplete() {
                           // Внутренний поток завершился
                        }
                    });
                } catch (Exception e) {
                    downstreamObserver.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                downstreamObserver.onError(e);
            }

            @Override
            public void onComplete() {
                done = true;
                // Завершаем основной поток только когда все внутренние потоки завершены
                // Для простоты, здесь мы завершаем сразу.
                downstreamObserver.onComplete();
            }
        }));
    }

    /**
     * Указывает Scheduler, в котором будет выполняться подписка (метод create).
     */
    public Observable<T> subscribeOn(Scheduler scheduler) {
        return create(observer -> scheduler.execute(() -> this.subscribe(observer)));
    }

    /**
     * Указывает Scheduler, в котором будут вызываться методы onNext, onError, onComplete у наблюдателя.
     */
    public Observable<T> observeOn(Scheduler scheduler) {
        return create(observer -> subscribe(new Observer<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                observer.onSubscribe(d);
            }

            @Override
            public void onNext(T t) {
                scheduler.execute(() -> observer.onNext(t));
            }

            @Override
            public void onError(Throwable e) {
                scheduler.execute(() -> observer.onError(e));
            }

            @Override
            public void onComplete() {
                scheduler.execute(observer::onComplete);
            }
        }));
    }

    // Внутренний класс для простого управления подпиской
    private static class BasicDisposable implements Disposable {
        private volatile boolean disposed = false;

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
