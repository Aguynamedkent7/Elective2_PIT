package app.numlab.ui.chart

import androidx.compose.ui.graphics.Color
import app.numlab.domain.format.Fmt
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A function to plot; [f] returns NaN (or throws) where undefined. */
data class Curve(
    val f: (Double) -> Double,
    val color: Color,
    val width: Float = 3f,
    val dashed: Boolean = false,
    val fit: Boolean = true,
    val probe: Boolean = true,
    val label: String = "f(x)",
)

enum class PointKind { DATA, ITER, ROOT, TARGET, TRUE }

data class PlotPoint(
    val kind: PointKind,
    val x: Double,
    val y: Double,
    val label: String? = null,
    val color: Color? = null,
    val alpha: Float = 1f,
    val dragId: Int? = null,
)

sealed interface Overlay {
    data class Band(val x0: Double, val x1: Double, val color: Color, val alpha: Float = 0.13f) : Overlay
    data class VLine(val x: Double, val color: Color, val dashed: Boolean = true, val width: Float = 1.6f, val draggable: Boolean = false) : Overlay
    data class Seg(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val color: Color, val dashed: Boolean = false, val width: Float = 2.2f) : Overlay
}

/** Values beyond this are treated as gaps (PLAN §6.3). */
const val HUGE = 1e100

/** f(x) with errors, non-finite and huge values mapped to NaN. */
fun sampleAt(f: (Double) -> Double, x: Double): Double = try {
    f(x).takeIf { it.isFinite() && abs(it) <= HUGE } ?: Double.NaN
} catch (_: RuntimeException) {
    Double.NaN
}

/** [n]+1 evenly spaced samples of [f] over [x0, x1], as interleaved x/y. */
fun sample(f: (Double) -> Double, x0: Double, x1: Double, n: Int): DoubleArray {
    val out = DoubleArray(2 * (n + 1))
    for (i in 0..n) {
        val x = x0 + (x1 - x0) * i / n
        out[2 * i] = x
        out[2 * i + 1] = sampleAt(f, x)
    }
    return out
}

/** Initial x-range around [xs] with padding fraction [k] (prototype `domainFor`). */
fun domainFor(xs: List<Double>, k: Double = 0.35): Pair<Double, Double> {
    val f = xs.filter { it.isFinite() }
    if (f.isEmpty()) return -1.0 to 1.0
    val lo = f.min()
    val hi = f.max()
    val span = (hi - lo).takeIf { it > 0 } ?: 2.0
    return lo - span * k to hi + span * k
}

/** Y-range from the 1st–99th percentile of fitted samples plus points, padded 14 %. */
fun yRange(samples: List<DoubleArray>, fits: List<Boolean>, points: List<PlotPoint>, x0: Double, x1: Double, includeZero: Boolean): Pair<Double, Double> {
    val ys = ArrayList<Double>()
    samples.forEachIndexed { ci, s -> if (fits[ci]) for (i in 1 until s.size step 2) if (s[i].isFinite()) ys += s[i] }
    ys.sort()
    var lo = if (ys.isEmpty()) -1.0 else ys[floor(ys.size * 0.01).toInt()]
    var hi = if (ys.isEmpty()) 1.0 else ys[min(ys.size - 1, ceil(ys.size * 0.99).toInt() - 1).coerceAtLeast(0)]
    if (includeZero) { lo = min(lo, 0.0); hi = max(hi, 0.0) }
    points.forEach { if (it.x in x0..x1 && it.y.isFinite()) { lo = min(lo, it.y); hi = max(hi, it.y) } }
    if (!lo.isFinite() || !hi.isFinite() || abs(lo) > HUGE || abs(hi) > HUGE) { lo = -1.0; hi = 1.0 }
    if (hi <= lo) { lo -= 1; hi += 1 }
    val sp = hi - lo
    return lo - sp * 0.14 to hi + sp * 0.14
}

private fun niceStep(raw: Double): Double {
    val p = 10.0.pow(floor(log10(raw)))
    val f = raw / p
    return (if (f < 1.5) 1.0 else if (f < 3) 2.0 else if (f < 7) 5.0 else 10.0) * p
}

/** Nice tick values over [lo, hi] aiming for about [n] ticks; returns (ticks, step). */
fun ticks(lo: Double, hi: Double, n: Int): Pair<List<Double>, Double> {
    val st = niceStep((hi - lo) / n)
    val out = ArrayList<Double>()
    var v = ceil(lo / st - 1e-9) * st
    while (v <= hi + 1e-9 && out.size < 200) {
        out += if (abs(v) < st * 1e-6) 0.0 else BigDecimal(v).round(MathContext(12)).toDouble()
        v += st
    }
    return out to st
}

fun tickLabel(v: Double, st: Double): String = when {
    v == 0.0 -> "0"
    abs(v) >= 1e5 || abs(v) < 1e-4 -> Fmt.toExponential(v, 0)
    else -> Fmt.toFixed(v, (-floor(log10(st) + 1e-9)).toInt().coerceIn(0, 6))
}
