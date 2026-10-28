package app.numlab.domain.interp

import app.numlab.domain.format.Fmt.jsNum
import app.numlab.domain.format.Fmt.toFixed
import app.numlab.domain.parser.CompiledFn
import app.numlab.domain.roots.safe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** Result of [parsePoints]: rows as typed, plus whether a trailing number was left over. */
data class ParsedPoints(val rows: List<PointRow>, val odd: Boolean, val count: Int)

private val NUM = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
private val NEWLINES = Regex("\n+")

/** Accepts `0 0, 1 0.84`, `(0,0) (1,0.84)`, or one `x y` pair per line (spreadsheet paste). */
fun parsePoints(text: String): ParsedPoints {
    val lines = text.split(NEWLINES).map { it.trim() }.filter { it.isNotEmpty() }
    val perLine = lines.map { l -> NUM.findAll(l).map { it.value }.toList() }
    val nums = if (lines.size > 1 && perLine.all { it.size == 2 }) {
        perLine.flatten()
    } else {
        NUM.findAll(text).map { it.value }.toList()
    }
    val rows = (0 until nums.size / 2).map { PointRow(nums[2 * it], nums[2 * it + 1]) }
    return ParsedPoints(rows, nums.size % 2 == 1, nums.size)
}

enum class NodeKind { EQUAL, CHEBYSHEV }

/** n nodes on [a, b], equally spaced or Chebyshev, sorted ascending. */
fun nodeXs(a: Double, b: Double, n: Int, kind: NodeKind): List<Double> = (0 until n).map { i ->
    if (kind == NodeKind.CHEBYSHEV) (a + b) / 2 + (b - a) / 2 * cos((2 * i + 1) * PI / (2 * n))
    else a + (b - a) * i / (n - 1)
}.sorted()

/** Sample rows for presets (e.g. Runge: 7 equal nodes of `1/(1+25x²)` on [−1, 1]). */
fun genNodes(f: CompiledFn, a: Double, b: Double, n: Int, kind: NodeKind): List<PointRow> =
    nodeXs(a, b, n, kind).map { x ->
        PointRow(jsNum(toFixed(x, 6).toDouble()), jsNum(toFixed(f.eval(x), 6).toDouble()))
    }

/** Max |f − P| over [a, b] for n = nmin..nmax, equal vs Chebyshev nodes. */
data class NodeError(val n: Int, val equal: Double, val cheb: Double)

fun errorByN(f: CompiledFn, a: Double, b: Double, nmin: Int = 2, nmax: Int = 10): List<NodeError> =
    (nmin..nmax).map { n ->
        val (equal, cheb) = NodeKind.entries.map { kind ->
            val xs = nodeXs(a, b, n, kind)
            val ys = xs.map { f.safe(it) }
            var m = 0.0
            for (s in 0..160) {
                val x = a + (b - a) * s / 160
                val t = f.safe(x)
                if (t.isFinite()) m = max(m, abs(t - lagEval(xs, ys, x)))
            }
            m
        }
        NodeError(n, equal, cheb)
    }

private fun lagEval(xs: List<Double>, ys: List<Double>, x: Double): Double {
    var s = 0.0
    for (i in xs.indices) {
        var l = 1.0
        for (j in xs.indices) if (j != i) l *= (x - xs[j]) / (xs[i] - xs[j])
        s += ys[i] * l
    }
    return s
}
