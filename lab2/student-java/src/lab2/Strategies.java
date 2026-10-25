package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() {
    }

    /**
     * Всегда первый поток из enabled.
     */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /**
     * Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1).
     */
    public static final class Replay implements Strategy {
        private int _pos = 0;
        private final String _trace;

        public Replay(String trace) {
            _trace = trace;
        }

        /**
         * Сбросить позицию в трассе перед прогоном.
         */
        public void beginRun() {
            _pos = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            char next = _trace.charAt(_pos++);
            return next - 'A';
        }
    }

    /**
     * Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed).
     */
    public static final class RandomPick implements Strategy {
        private final Random _rnd;

        public RandomPick(long seed) {
            _rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(_rnd.nextInt(enabled.size()));
        }
    }

    /**
     * Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     * bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE.
     */
    public static final class Dfs implements Strategy {

        private final ScheduleNode _schedule = new ScheduleNode(-1);
        private List<ScheduleNode> _currentTrace = new ArrayList<>(List.of(_schedule));

        private int _bounds;
        private int _currBounds = 0;
        private int _pos = 0;

        public Dfs(int bound) {
            _bounds = bound;
        }

        /**
         * Подготовиться к новому прогону (позиция в пути, счётчик вытеснений).
         */
        public void beginRun() {
            _pos = 0;
            _currBounds = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            _pos++;

            int pick;
            if (_pos < _currentTrace.size()) {
                pick = _currentTrace.get(_pos).name;
            } else {
                List<Integer> options =
                        _currBounds >= _bounds && enabled.contains(last)
                                ? List.of(last)
                                : enabled;
                ScheduleNode prev = _currentTrace.get(_pos - 1);
                prev.children = options.stream().map(ScheduleNode::new).toList();
                ScheduleNode next = prev.children.getFirst();

                pick = next.name;
                _currentTrace.add(next);
            }

            if (last != -1 && enabled.contains(last) && pick != last) {
                _currBounds++;
            }

            return pick;
        }

        /**
         * Выбрать следующий путь; false — дерево обойдено.
         */
        public boolean endRun() {
            for (int i = _currentTrace.size() - 1; i > -1; i--) {
                ScheduleNode node = _currentTrace.get(i);
                if (node.children.stream().allMatch(ScheduleNode::getVisited)) {
                    node.visited = true;
                }
            }

            _currentTrace = new ArrayList<>();
            return dfs(_schedule);
        }

        private boolean dfs(ScheduleNode node) {
            if (node.visited) {
                return false;
            }

            _currentTrace.add(node);

            if (node.children.isEmpty()) {
                return true;
            }

            for (ScheduleNode child : node.children) {
                if (dfs(child)) {
                    return true;
                }
            }

            _currentTrace.removeLast();
            return false;
        }

        private class ScheduleNode {
            boolean visited = false;
            List<ScheduleNode> children = new ArrayList<>();

            int name;

            public ScheduleNode(int name) {
                this.name = name;
            }

            public boolean getVisited() {
                return visited;
            }
        }
    }
}
