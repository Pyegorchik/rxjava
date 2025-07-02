public interface Observer<T> {
    /**
     * Вызывается при получении нового элемента.
     */
    void onNext(T t);

    /**
     * Вызывается при возникновении ошибки в потоке.
     */
    void onError(Throwable e);

    /**
     * Вызывается при успешном завершении потока.
     * После вызова этого метода другие методы не вызываются.
     */
    void onComplete();
    
    // /**
    //  * Предоставляет Disposable для управления подпиской.
    //  */
    // void onSubscribe(Disposable d);
}