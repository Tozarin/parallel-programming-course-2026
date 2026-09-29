package collectors

import collectors.MetricsCollector.Companion.BUCKETS_COUNT
import collectors.MetricsCollector.Companion.percentile
import values.MAX_VALUE
import values.MIN_VALUE
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.math.max
import kotlin.math.min

class MetricsCollectorV3 : MetricsCollector {

    private val _threadStates = mutableListOf<ThreadState>()
    private val _statesLock = Any()

    private val _myState: ThreadLocal<ThreadState> =
        ThreadLocal.withInitial {
            ThreadState().also {
                synchronized(_statesLock) {
                    _threadStates.add(it)
                }
            }
        }

    override fun record(value: Int) {
        val (buckets, sum, count, min, max) = _myState.get()
        val bucketIndex = min(value / 4, BUCKETS_COUNT - 1)

        buckets.setRelease(bucketIndex, buckets.getPlain(bucketIndex) + 1)
        count.setRelease(count.plain + 1)
        sum.setRelease(sum.plain + value)

        if (min.plain > value) min.setRelease(value)
        if (max.plain < value) max.setRelease(value)
    }

    override fun snapshot(): Snapshot {
        val states = mutableListOf<ThreadState>()
        synchronized(_statesLock) {
            _threadStates.forEach(states::add)
        }

        val buckets = LongArray(BUCKETS_COUNT)
        var sum = 0L
        var count = 0L
        var min = MAX_VALUE
        var max = MIN_VALUE

        states.forEach { (sbuckets, ssum, scount, smin, smax) ->
            for (i in 0 until BUCKETS_COUNT) {
                buckets[i] += sbuckets.get(i)
            }

            sum += ssum.get()
            count += scount.get()

            min = min(min, smin.get())
            max = max(max, smax.get())
        }

        val p50 = percentile(0.50F, count, buckets)
        val p99 = percentile(0.99F, count, buckets)

        return Snapshot(
            buckets = buckets,
            sum = sum,
            count = count,
            min = min,
            max = max,
            p50 = p50,
            p99 = p99
        )
    }

    override fun reset() {
        synchronized(_statesLock) {
            _threadStates.clear()
        }
    }
}

private data class ThreadState(
    val buckets: AtomicLongArray = AtomicLongArray(BUCKETS_COUNT),
    val sum: AtomicLong = AtomicLong(0),
    val count: AtomicLong = AtomicLong(0),
    val min: AtomicInteger = AtomicInteger(MAX_VALUE),
    val max: AtomicInteger = AtomicInteger(MIN_VALUE)
)
