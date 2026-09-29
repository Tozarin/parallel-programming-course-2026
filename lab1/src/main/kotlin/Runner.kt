import collectors.MetricsCollector
import collectors.Snapshot
import java.lang.System.currentTimeMillis
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Runner(
    val collector: MetricsCollector,
    val values: IntArray
) {

    private var latch = CountDownLatch(1)
    private var stop = AtomicBoolean(false)

    private data class Threads(
        val results: LongArray,
        val threads: Array<Thread>
    )

    private fun buildThreads(n: Int): Threads {
        latch = CountDownLatch(1)
        stop = AtomicBoolean(false)
        val latch = latch
        val stop = stop

        val results = LongArray(n)
        val threads = Array(n) { k ->
            Thread {
                var lCount = 0L
                var i = k * START_OFFSET % values.size
                latch.await()

                while (!stop.get()) {
                    collector.record(values[i])
                    lCount++
                    i = if (i == values.lastIndex) 0 else i + 1
                }

                results[k] = lCount
            }.also(Thread::start)
        }

        return Threads(results, threads)
    }

    private fun run(n: Int, t: Duration): Double {
        val (results, threads) = buildThreads(n)

        val startTime = currentTimeMillis()
        latch.countDown()
        sleep(t.inWholeMilliseconds)
        stop.set(true)
        val endTime = currentTimeMillis()

        threads.forEach(Thread::join)

        return results.sum() / (endTime - startTime).toDouble() * 1000
    }

    private fun warmpup(n: Int) {
        run(n, WARMUP)
        collector.reset()
    }

    fun measurePoint(n: Int, t: Duration): ExperimentResults {
        warmpup(n)

        val results = mutableListOf<Double>()
        repeat(EXPERIMENT_COUNT) {
            results.add(run(n, t))
        }

        return ExperimentResults(
            results.sorted()[EXPERIMENT_COUNT / 2],
            collector.snapshot()
        )
    }

    fun checkConsistency(n: Int, doubleCollect: Boolean = false): ConsistencyResults {
        warmpup(n)

        val (results, threads) = buildThreads(n)

        var less = 0
        var greater = 0

        latch.countDown()

        repeat(SNAPSHOT_COUNT) {
            val snapshot = collector.snapshot()
            val bucketsSum = snapshot.buckets.sum()
            if (bucketsSum < snapshot.count) {
                less++
            } else if (bucketsSum > snapshot.count) {
                greater++
            }
        }

        stop.set(true)

        threads.forEach(Thread::join)

        if (doubleCollect) {
            collector.snapshot()
        }

        return ConsistencyResults(
            snapshots = SNAPSHOT_COUNT,
            less = less,
            greater = greater,
            recorded = results.sum(),
            finalCount = collector.snapshot().count
        )
    }

    companion object {
        const val START_OFFSET = 12345
        const val SNAPSHOT_COUNT = 10_000

        const val EXPERIMENT_COUNT = 5
        val WARMUP = 5.seconds
    }
}

data class ExperimentResults(
    val ops: Double,
    val stats: Snapshot,
) {
    fun print() {
        println("OPS: ${"%.0f".format(ops)}")
        val (_, count, sum, min, max, p50, p99) = stats
        println("COUNT: $count, SUM: $sum, MIN: $min, MAX: $max, P50: $p50, P99: $p99")
    }
}

data class ConsistencyResults(
    val snapshots: Int,
    val less: Int,
    val greater: Int,
    val recorded: Long,
    val finalCount: Long
) {
    fun print() {
        val broken = less + greater
        println("BROKEN: $broken / $snapshots (${"%.2f".format(broken * 100.0 / snapshots)}%)")
        println("LESS: $less, GREATER: $greater")
        println("RECORDED: $recorded, FINAL COUNT: $finalCount")
    }
}
