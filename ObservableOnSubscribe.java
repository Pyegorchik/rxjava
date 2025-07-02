@FunctionalInterface
public interface ObservableOnSubscribe<T> {
    void subscribe(Observer<T> observer) throws Exception;
}