# 1. Архитектура системы
Основана на реактивном программировании и паттерне "Наблюдатель". Ключевые компоненты:
- Observable<T>: Источник данных. Операторы (map, filter) возвращают новый Observable, формируя цепочки.

- Observer<T>: Обрабатывает события:
  - onNext(T item) — элемент,

  - onError(Throwable t) — ошибка,

  - onComplete() — завершение.

- Disposable: Управление подпиской (упрощённая реализация).

- Scheduler: Управление потоками выполнения.

Принцип работы цепочки:
При вызове subscribe() создаются связанные Observer-ы (например, MapObserver). Элементы проходят преобразования сверху вниз по цепочке.

# 2. Schedulers (Планировщики)

- subscribeOn(Scheduler):
    Задаёт поток для всей цепочки вверх (выполнение кода в Observable.create()). Действует только первый вызов.
- observeOn(Scheduler):
    Переключает поток для дальнейшей обработки вниз (вызовы onNext, onError, onComplete).

Реализованные планировщики:

- Schedulers.io() (IOThreadScheduler):
    Executors.newCachedThreadPool(). Для I/O-операций (сеть, файлы).

- Schedulers.computation() (ComputationScheduler):
    Фиксированный пул потоков (по числу ядер CPU). Для вычислений.

- Schedulers.single() (SingleThreadScheduler):
    Один поток для последовательных задач (например, БД).

# 3. Тестирование
Unit-тесты покрывают:

- Базовую функциональность (create(), передача элементов/ошибок).

- Операторы: map, filter, flatMap.

- Обработку ошибок.
  
- Корректность потоков: subscribeOn/observeOn.

- Отмену подписки (Disposable).

# 4. Скриншот работы

![Screenshot_2025-03-07_1751495457](https://github.com/user-attachments/assets/a432db56-db0e-43e1-b95c-5787ed947c70)

