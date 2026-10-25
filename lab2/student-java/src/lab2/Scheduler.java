package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /**
     * Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.
     */
    boolean logging;
    /**
     * Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100".
     */
    final List<String> log = new ArrayList<>();
    /**
     * null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2).
     */
    String deadlock;

    private final List<Th> _threads = new ArrayList<>();
    private List<Integer> _enabled = new ArrayList<>();
    private final Semaphore _sem = new Semaphore(0);

    private Th _current = null;
    private Th _last = null;

    private final List<String> _trace = new ArrayList<>();

    private boolean _isAborted = false;
    private final List<SchedLock> _locks = new ArrayList<>();

    public Scheduler() {
    }

    /**
     * Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.
     */
    public void spawn(Runnable body) {
        Th thread = new Th(body, _threads.size(), this);
        _threads.add(thread);
        _enabled.add(thread.getIx());
    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        _currentScheduler = this;

        if (_threads.isEmpty()) {
            throw new ArithmeticException("No threads registred");
        }

        _threads.forEach(Th::start);

        while (true) {
            _enabled = _threads.stream().filter(
                    th -> !th.isDone && th.cond.getAsBoolean()
            ).map(Th::getIx).toList();

            if (_enabled.isEmpty()) {
                end();
                return;
            }
            ;

            int lastIx = _last == null ? -1 : _last._ix;
            int nextIx = strategy.pick(_enabled, lastIx);

            Th thread = _threads.get(nextIx);
            thread.resetCond();
            logTrace(thread);
            _current = thread;
            thread.release();
            _sem.acquireUninterruptibly();
            _last = _current;
        }
    }

    private void end() {
        List<Th> isNotDone = new ArrayList<>();
        _threads.forEach(th -> {
            if (!th.isDone) {
                isNotDone.add(th);
            }
        });

        if (isNotDone.isEmpty()) {
            deadlock = null;
        } else {

            _isAborted = true;
            isNotDone.forEach(th -> {
                th.release();
                th.join();
            });

            List<String> locked = _locks.stream()
                    .filter(lock -> lock.lockedBy != null)
                    .map(lock -> lock.name + ":" + lock.lockedBy)
                    .toList();

            deadlock = "held={" + String.join(", ", locked) + "}";
        }
    }

    public static void log(String op) {
        Scheduler scheduler = _currentScheduler;
        if (scheduler.logging) {
            String s = scheduler._current.name + "  " + op;
            scheduler.log.add(s);
        }
    }

    private void logTrace(Th thread) {
        _trace.add(thread.name);
    }

    public void registerLock(SchedLock lock) {
        _locks.add(lock);
    }

    /**
     * Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB".
     */
    public String traceString() {
        return String.join("", _trace);
    }

    private void pause(BooleanSupplier cond) {
        Th current = _current;
        current.cond = cond;
        current.isTouched = false;
        _sem.release();
        current.stop();
    }

    private static Scheduler _currentScheduler;

    private void touchCurrent() {
        _current.isTouched = true;
    }

    static String currentName() {
        return _currentScheduler._current.name;
    }

    static void beforeShared(BooleanSupplier cond) {
        Scheduler scheduler = _currentScheduler;
        Th current = scheduler._current;
        if (current.isTouched || !cond.getAsBoolean()) scheduler.pause(cond);
        scheduler.touchCurrent();
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна.
     */
    public static void point() {
        log("window");
        _currentScheduler.touchCurrent();
    }

    /**
     * Ждать, пока cond не станет true. До этого поток не попадает в enabled.
     */
    public static void await(BooleanSupplier cond) {
        beforeShared(cond);
        log("await done");
    }

    private void toNextThread() {
        _sem.release();
    }

    private class Th {
        String name;
        private final int _ix;
        boolean isDone = false;
        boolean isTouched = false;

        private final Thread _thread;
        private final Semaphore _sem = new Semaphore(0);

        BooleanSupplier cond = () -> true;

        public Th(Runnable body, int ix, Scheduler scheduler) {
            _ix = ix;
            name = String.valueOf((char) ('A' + _ix));
            _thread = new Thread(() -> {
                _sem.acquireUninterruptibly();
                try {
                    body.run();
                } catch (Aborted _) { }
                finally {
                    isDone = true;
                    if (!_isAborted) scheduler.toNextThread();
                }
            });
        }

        public void start() {
            _thread.start();
        }

        public void join() {
            try {
                _thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public int getIx() {
            return _ix;
        }

        public void release() {
            _sem.release();
        }

        public void stop() {
            _sem.acquireUninterruptibly();
            if (_isAborted) throw new Aborted();
        }

        private static class Aborted extends RuntimeException {}

        public void resetCond() {
            cond = () -> true;
        }
    }
}
