package collectors

object NoopMetricsCollector : MetricsCollector {
    override fun record(value: Int) {}

    override fun snapshot(): Snapshot = Snapshot.EMPTY

    override fun reset() {}
}
