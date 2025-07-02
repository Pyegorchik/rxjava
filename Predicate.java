@FunctionalInterface
public interface Predicate<T> {
    boolean test(T t) throws Exception;
}
