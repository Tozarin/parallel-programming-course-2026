package collectors

import collectors.MetricsCollector.Companion.BUCKETS_COUNT
import collectors.MetricsCollector.Companion.percentile
import collectors.ThreadBuffer.Companion.NOWHERE
import values.MAX_VALUE
import values.MIN_VALUE
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

open class MetricsCollectorV4 : MetricsCollector {

    private val _threadBuffers = mutableListOf<ThreadBuffer>()
    private val _buffersLock = Any()

    @Volatile
    internal var _active = 0

    private var _globalBuckets = LongArray(BUCKETS_COUNT)
    private var _globalSum = 0L
    private var _globalCount = 0L
    private var _globalMin = MAX_VALUE
    private var _globalMax = MIN_VALUE

    internal val _myBuffers: ThreadLocal<ThreadBuffer> =
        ThreadLocal.withInitial {
            ThreadBuffer().also {
                synchronized(_buffersLock) {
                    _threadBuffers.add(it)
                }
            }
        }

    override fun record(value: Int) {
        val (buckets, sum, count, min, max, inside) = _myBuffers.get()

        var buffer: Int
        while (true) {
            buffer = _active
            inside.set(buffer)
            if (_active == buffer) {
                break
            }
            inside.setRelease(NOWHERE)
        }

        val bucketIndex = min(value / 4, BUCKETS_COUNT - 1)

        buckets[buffer][bucketIndex]++
        sum[buffer] += value
        count[buffer]++

        min[buffer] = min(value, min[buffer])
        max[buffer] = max(value, max[buffer])
        inside.setRelease(NOWHERE)
    }

    override fun snapshot(): Snapshot {
        synchronized(_buffersLock) {
            val oldBuffer = _active
            _active = 1 - oldBuffer

            _threadBuffers.forEach { buffers ->
                while (buffers.inside.get() == oldBuffer) {}
            }

            _threadBuffers.forEach { (bbuckets, bsum, bcount, bmin, bmax, _) ->
                for (i in 0 until BUCKETS_COUNT) {
                    _globalBuckets[i] += bbuckets[oldBuffer][i]
                    bbuckets[oldBuffer][i] = 0L
                }

                _globalSum += bsum[oldBuffer]
                _globalCount += bcount[oldBuffer]

                _globalMin = min(_globalMin, bmin[oldBuffer])
                _globalMax = max(_globalMax, bmax[oldBuffer])

                bsum[oldBuffer] = 0L
                bcount[oldBuffer] = 0L
                bmin[oldBuffer] = MAX_VALUE
                bmax[oldBuffer] = MIN_VALUE
            }

            val p50 = percentile(0.50F, _globalCount, _globalBuckets)
            val p99 = percentile(0.99F, _globalCount, _globalBuckets)

            return Snapshot(
                buckets = _globalBuckets,
                sum = _globalSum,
                count = _globalCount,
                min = _globalMin,
                max = _globalMax,
                p50 = p50,
                p99 = p99
            )
        }
    }

    override fun reset() {
        synchronized(_buffersLock) {
            _threadBuffers.clear()

            _globalBuckets = LongArray(BUCKETS_COUNT)
            _globalSum = 0L
            _globalCount = 0L
            _globalMin = MAX_VALUE
            _globalMax = MIN_VALUE
        }
    }
}

class MetricsCollectorV4Unsafe : MetricsCollectorV4() {
    override fun record(value: Int) {
        val (buckets, sum, count, min, max, inside) = _myBuffers.get()

        val buffer = _active
        inside.set(buffer)

        val bucketIndex = min(value / 4, BUCKETS_COUNT - 1)

        buckets[buffer][bucketIndex]++
        sum[buffer] += value
        count[buffer]++

        min[buffer] = min(value, min[buffer])
        max[buffer] = max(value, max[buffer])
        inside.setRelease(NOWHERE)
    }
}

internal data class ThreadBuffer(
    val buckets: Array<LongArray> = Array(2) { LongArray(BUCKETS_COUNT) },
    val sum: LongArray = LongArray(2),
    val count: LongArray = LongArray(2),
    val min: IntArray = IntArray(2) { MAX_VALUE },
    val max: IntArray = IntArray(2) { MIN_VALUE },

    val inside: AtomicInteger = AtomicInteger(NOWHERE)
) {
    companion object {
        const val NOWHERE = -1
    }
}
