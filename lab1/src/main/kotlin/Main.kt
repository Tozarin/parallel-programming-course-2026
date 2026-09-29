import collectors.MetricsCollectorV0
import collectors.MetricsCollectorV1
import collectors.MetricsCollectorV1Noop
import collectors.MetricsCollectorV2
import collectors.MetricsCollectorV3
import collectors.MetricsCollectorV4
import collectors.MetricsCollectorV4Unsafe
import values.genValues
import kotlin.time.Duration.Companion.seconds

fun main() {
    val values = genValues(SEED)

//    val collector = MetricsCollectorV0()
    val collector = MetricsCollectorV1()
//    val collector = MetricsCollectorV1Noop()
//    val collector = MetricsCollectorV2()
//    val collector = MetricsCollectorV3()
//    val collector = MetricsCollectorV4()
//    val collector = MetricsCollectorV4Unsafe()

    Runner(collector, values).measurePoint(n = 8, t = 5.seconds).print()
//    Runner(collector, values).checkConsistency(n = 4, doubleCollect = true).print()
}

const val SEED = 1377
