package collectors

import collectors.MetricsCollector.Companion.BUCKETS_COUNT
import collectors.MetricsCollector.Companion.percentile
import values.MAX_VALUE
import values.MIN_VALUE
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV0 : MetricsCollector {

    private var _buckets = LongArray(BUCKETS_COUNT)

    private var _sum = 0L
    private var _count = 0L
    private var _min = MAX_VALUE
    private var _max = MIN_VALUE

    override fun record(value: Int) {
        _sum += value
        _count++
        _min = min(_min, value)
        _max = max(_max, value)
        _buckets[min(value / 4, BUCKETS_COUNT - 1)]++
    }

    override fun snapshot(): Snapshot {
        val buckets = _buckets.copyOf()

        return Snapshot(
            buckets = buckets,
            count = _count,
            sum = _sum,
            min = _min,
            max = _max,
            p50 = percentile(0.50F, _count, buckets),
            p99 = percentile(0.99F, _count, buckets),
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
