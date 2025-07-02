@FunctionalInterface
public interface ObservableOnSubscribe<T> {
    void subscribe(Emitter<T> observer) throws Exception;
}