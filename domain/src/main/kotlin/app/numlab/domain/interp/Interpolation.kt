package app.numlab.domain.interp

import app.numlab.domain.format.Fmt.sig5
import app.numlab.domain.model.DomainException
import app.numlab.domain.model.parseNumber
import app.numlab.domain.parser.CompiledFn
import app.numlab.domain.parser.ExprParser
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Serializable
enum class InterpMethod { LAGRANGE, NEWTON_DD }

/** Where the "true" value comes from for error metrics. */
@Serializable
enum class Truth { NONE, FN, VALUE }

/** One editable data row, kept as typed. */
@Serializable
data class PointRow(val x: String, val y: String)

@Serializable
data class InterpInput(
    val method: InterpMethod,
    val rows: List<PointRow>,
    val xEval: String,
    val truth: Truth = Truth.NONE,
    val fx: String = "",
    val yTrue: String = "",
)

data class Point(val x: Double, val y: Double)

/** A Lagrange basis term evaluated at xₑ. */
data class BasisTerm(val i: Int, val x: Double, val y: Double, val l: Double, val term: Double)

/** Theoretical bound `M/(n+1)!·|∏(xₑ − xᵢ)|`. */
data class ErrorBound(
    val m: Double,
    val fact: Double,
    val prod: Double,
    val value: Double,
    val k: Int,
    val extrapolating: Boolean,
)

/** [rel] and [absRel] are percentages; null when the true value is 0. */
data class ErrorMetrics(
    val yTrue: Double,
    val abs: Double,
    val rel: Double?,
    val absRel: Double?,
    val bound: ErrorBound?,
)

data class InterpResult(
    val method: InterpMethod,
    val points: List<Point>,
    val n: Int,
    val xe: Double,
    val p: (Double) -> Double,
    val pxe: Double,
    val coeffs: List<Double>,
    val poly: String,
    val polyForm: String,
    val dd: List<List<Double>>,
    val basis: List<BasisTerm>?,
    val metrics: ErrorMetrics?,
    val fTrue: CompiledFn?,
)

private fun polyMul(p: List<Double>, q: List<Double>): List<Double> {
    val r = DoubleArray(p.size + q.size - 1)
    p.forEachIndexed { i, a -> q.forEachIndexed { j, b -> r[i + j] += a * b } }
    return r.toList()
}

private fun polyAdd(p: List<Double>, q: List<Double>): List<Double> {
    val r = DoubleArray(max(p.size, q.size))
    p.forEachIndexed { i, a -> r[i] += a }
    q.forEachIndexed { i, a -> r[i] += a }
    return r.toList()
}

private val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
private fun sup(n: Int) = n.toString().map { SUP[it - '0'] }.joinToString("")

/** Expanded polynomial as `−0.010383x³ − 0.3557x² + 1.2076x` (coefficients low → high). */
fun polyToString(c: List<Double>): String {
    val out = StringBuilder()
    for (k in c.indices.reversed()) {
        val v = c[k]
        if (abs(v) < 1e-9) continue
        val mag = sig5(abs(v))
        val body = if (k == 0) mag else "${if (mag == "1") "" else mag}x${if (k > 1) sup(k) else ""}"
        if (out.isEmpty()) out.append(if (v < 0) "−" else "").append(body)
        else out.append(if (v < 0) " − " else " + ").append(body)
    }
    return out.ifEmpty { "0" }.toString()
}

private fun xTerm(xj: Double) = when {
    xj == 0.0 -> "x"
    xj > 0 -> "(x − ${sig5(xj)})"
    else -> "(x + ${sig5(-xj)})"
}

private fun binom(n: Int, k: Int): Double {
    var r = 1.0
    for (i in 1..k) r = r * (n - k + i) / i
    return r
}

/** k-th derivative by central finite difference with step [h]. */
fun nthDeriv(f: CompiledFn, x: Double, k: Int, h: Double): Double {
    var s = 0.0
    for (i in 0..k) s += (if (i % 2 == 1) -1 else 1) * binom(k, i) * f.eval(x + (k / 2.0 - i) * h)
    return s / h.pow(k)
}

/** Port of `interpolate` in math.js. Throws [DomainException]. */
fun interpolate(inp: InterpInput): InterpResult {
    val pts = inp.rows.mapNotNull { r ->
        val xs = r.x.trim()
        val ys = r.y.trim()
        if (xs.isEmpty() && ys.isEmpty()) null
        else Point(parseNumber(xs, "every x value"), parseNumber(ys, "every y value"))
    }.sortedBy { it.x }
    if (pts.size < 2) throw DomainException("Enter at least 2 data points")
    for (i in 1 until pts.size) if (pts[i].x == pts[i - 1].x) throw DomainException("x values must be distinct")
    val xe = parseNumber(inp.xEval, "x to evaluate")
    val n = pts.size - 1
    val xs = pts.map { it.x }
    val ys = pts.map { it.y }

    val dd = mutableListOf(ys)
    for (j in 1..n) {
        val prev = dd[j - 1]
        dd += (0..n - j).map { i -> (prev[i + 1] - prev[i]) / (xs[i + j] - xs[i]) }
    }
    val newtonEval = { x: Double ->
        var r = dd[n][0]
        for (k in n - 1 downTo 0) r = r * (x - xs[k]) + dd[k][0]
        r
    }
    val basisAt = { x: Double, i: Int ->
        var l = 1.0
        for (j in 0..n) if (j != i) l *= (x - xs[j]) / (xs[i] - xs[j])
        l
    }
    val lagrangeEval = { x: Double -> ys.indices.fold(0.0) { s, i -> s + ys[i] * basisAt(x, i) } }

    var coeffs = listOf(0.0)
    var polyForm = ""
    var basis: List<BasisTerm>? = null
    if (inp.method == InterpMethod.LAGRANGE) {
        for (i in 0..n) {
            var b = listOf(1.0)
            var den = 1.0
            for (j in 0..n) if (j != i) {
                b = polyMul(b, listOf(-xs[j], 1.0))
                den *= xs[i] - xs[j]
            }
            coeffs = polyAdd(coeffs, b.map { it * ys[i] / den })
        }
        basis = xs.indices.map { i ->
            val l = basisAt(xe, i)
            BasisTerm(i, xs[i], ys[i], l, ys[i] * l)
        }
    } else {
        var acc = listOf(dd[n][0])
        for (k in n - 1 downTo 0) acc = polyAdd(polyMul(acc, listOf(-xs[k], 1.0)), listOf(dd[k][0]))
        coeffs = acc
        polyForm = dd.mapIndexed { j, col -> sig5(col[0]) + (0 until j).joinToString("") { xTerm(xs[it]) } }
            .joinToString(" + ").replace("+ -", "− ").replace(Regex("^-"), "−")
    }
    val p: (Double) -> Double = if (inp.method == InterpMethod.LAGRANGE) lagrangeEval else newtonEval
    val pxe = p(xe)

    val fTrue = if (inp.truth == Truth.FN) ExprParser.compileOrThrow(inp.fx) else null
    val yTrue = when (inp.truth) {
        Truth.FN -> fTrue!!.eval(xe)
        Truth.VALUE -> parseNumber(inp.yTrue, "true value")
        Truth.NONE -> null
    }
    val metrics = yTrue?.let {
        val rel = if (it == 0.0) null else (it - pxe) / it * 100
        ErrorMetrics(it, abs(it - pxe), rel, rel?.let(::abs), fTrue?.let { f -> bound(f, xs, xe) })
    }
    return InterpResult(
        method = inp.method, points = pts, n = n, xe = xe, p = p, pxe = pxe, coeffs = coeffs,
        poly = polyToString(coeffs), polyForm = polyForm, dd = dd, basis = basis, metrics = metrics, fTrue = fTrue,
    )
}

private fun bound(f: CompiledFn, xs: List<Double>, xe: Double): ErrorBound? {
    val n = xs.size - 1
    val k = n + 1
    if (k > 8) return null
    val lo = min(xs[0], xe)
    val hi = max(xs[n], xe)
    val h = max((hi - lo) / 30, 1e-3)
    var m: Double? = null
    for (s in 0..60) {
        val xi = lo + (hi - lo) * s / 60
        val v = try {
            abs(nthDeriv(f, xi, k, h))
        } catch (_: DomainException) {
            continue
        }
        if (v.isFinite() && (m == null || v > m)) m = v
    }
    if (m == null) return null
    var fact = 1.0
    for (i in 2..k) fact *= i
    val prod = xs.fold(1.0) { a, xi -> a * (xe - xi) }
    return ErrorBound(m, fact, prod, m / fact * abs(prod), k, xe < xs[0] || xe > xs[n])
}
