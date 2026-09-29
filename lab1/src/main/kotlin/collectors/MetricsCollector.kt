package collectors

interface MetricsCollector {
    fun record(value: Int)

    fun snapshot(): Snapshot

    fun reset()

    companion object {
        const val BUCKETS_COUNT = 256

        fun percentile(p: Float, count: Long, buckets: LongArray): Int {
            val threshold = count * p
            var c = 0L
            for (i in 0 until BUCKETS_COUNT) {
                c += buckets[i]
                if (c >= threshold) {
                    return i * 4
                }
            }

            return (BUCKETS_COUNT - 1) * 4
        }
    }
}
