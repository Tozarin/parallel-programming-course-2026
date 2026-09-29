package values

import kotlin.math.pow
import kotlin.random.Random

fun genValues(seed: Int): IntArray {
    val values = IntArray(VALUES_COUNT)

    val random = Random(seed)
    val cdf = zipfCdf()

    for (i in 0 until VALUES_COUNT) {
        val u = random.nextDouble()
        var index = cdf.binarySearch(u)

        if (index < 0) {
            index = -index - 1
        }

        values[i] = MIN_VALUE + index.coerceAtMost(cdf.lastIndex)
    }

    return values
}

private fun zipfCdf(): DoubleArray {
    val count = MAX_VALUE - MIN_VALUE + 1
    val cdf = DoubleArray(count)

    var sum = 0.0
    for (i in 0 until count) {
        sum += 1.0 / (i + 1).toDouble().pow(ZIPF_EXPONENT)
        cdf[i] = sum
    }

    for (i in 0 until count) {
        cdf[i] /= sum
    }

    return cdf
}

const val ZIPF_EXPONENT = 1.15

// 2 ** 20
const val VALUES_COUNT = 1_048_576

const val MAX_VALUE = 1023
const val MIN_VALUE = 1
