package collectors

data class Snapshot(
    val buckets: LongArray,
    val count: Long,
    val sum: Long,
    val min: Int,
    val max: Int,
    val p50: Int,
    val p99: Int
) {
    companion object {
        val EMPTY = Snapshot(
            buckets = LongArray(0),
            count = 0,
            sum = 0,
            min = 0,
            max = 0,
            p50 = 0,
            p99 = 0
        )
    }
}
