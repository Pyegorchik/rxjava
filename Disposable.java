public interface Disposable {
    /**
     * Отменяет подписку.
     */
    void dispose();

    /**
     * @return true, если подписка уже отменена.
     */
    boolean isDisposed();
}
