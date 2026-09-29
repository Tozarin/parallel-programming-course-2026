package collectors

import collectors.MetricsCollector.Companion.BUCKETS_COUNT
import collectors.MetricsCollector.Companion.percentile
import values.MAX_VALUE
import values.MIN_VALUE
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class MetricsCollectorV2 : MetricsCollector {

    private var _buckets = LongArray(BUCKETS_COUNT)

    private var _sum = AtomicLong(0)
    private var _count = AtomicLong(0)
    private var _min = AtomicInteger(MAX_VALUE)
    private var _max = AtomicInteger(MIN_VALUE)

    private val _locks = Array(LOCKS_COUNT) { Any() }

    override fun record(value: Int) {

        val bucketIndex = min(value / 4, BUCKETS_COUNT - 1)
        synchronized(_locks[bucketIndex % LOCKS_COUNT]) {
            _buckets[bucketIndex]++
        }

        _sum.addAndGet(value.toLong())
        _count.incrementAndGet()

        var currMin = _min.get()
        while (currMin > value && !_min.compareAndSet(currMin, value)) {
            currMin = _min.get()
        }

        var currMax = _max.get()
        while (currMax < value && !_max.compareAndSet(currMax, value)) {
            currMax = _max.get()
        }
    }

    override fun snapshot(): Snapshot {
        val buckets = LongArray(BUCKETS_COUNT)
        for (lock in 0 until LOCKS_COUNT) {
            synchronized(_locks[lock]) {
                for (i in lock until BUCKETS_COUNT step LOCKS_COUNT) {
                    buckets[i] = _buckets[i]
                }
            }
        }

        val count = _count.get()
        return Snapshot(
            buckets = buckets,
            count = count,
            sum = _sum.get(),
            min = _min.get(),
            max = _max.get(),
            p50 = percentile(0.50F, count, buckets),
            p99 = percentile(0.99F, count, buckets),
        )
    }

    override fun reset() {
        _buckets = LongArray(BUCKETS_COUNT)
        _sum = AtomicLong(0)
        _count = AtomicLong(0)
        _min = AtomicInteger(MAX_VALUE)
        _max = AtomicInteger(MIN_VALUE)
    }

    companion object {
        const val LOCKS_COUNT = 16
    }
}
