package collectors

import collectors.MetricsCollector.Companion.BUCKETS_COUNT
import collectors.MetricsCollector.Companion.percentile
import values.MAX_VALUE
import values.MIN_VALUE
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV1 : MetricsCollector {

    private var _buckets = LongArray(BUCKETS_COUNT)

    private var _sum = 0L
    private var _count = 0L
    private var _min = MAX_VALUE
    private var _max = MIN_VALUE

    private val _lock = Any()

    override fun record(value: Int) {
        synchronized(_lock) {
            _sum += value
            _count++
            _min = min(_min, value)
            _max = max(_max, value)
            _buckets[min(value / 4, BUCKETS_COUNT - 1)]++
        }
    }

    override fun snapshot(): Snapshot = synchronized(_lock) {
            val buckets = _buckets.copyOf()

            Snapshot(
                buckets = buckets,
                count = _count,
                sum = _sum,
                min = _min,
                max = _max,
                p50 = percentile(0.50F, _count, buckets),
                p99 = percentile(0.99F, _count, buckets)
            )
        }

    override fun reset() {
        _buckets = LongArray(BUCKETS_COUNT)
        _sum = 0L
        _count = 0L
        _min = MAX_VALUE
        _max = MIN_VALUE
    }
}


class MetricsCollectorV1Noop : MetricsCollector {

    private val _lock = Any()

    override fun record(value: Int) {
        synchronized(_lock) {

        }
    }

    override fun snapshot(): Snapshot = synchronized(_lock) {
        return Snapshot.EMPTY
    }

    override fun reset() {}
}
