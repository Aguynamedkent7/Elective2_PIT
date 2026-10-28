package app.numlab.domain.roots

import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.format.Fmt.fmtE
import app.numlab.domain.format.Fmt.jsNum
import app.numlab.domain.format.Fmt.toFixed
import app.numlab.domain.model.DomainException
import app.numlab.domain.model.domainCatching
import app.numlab.domain.parser.CompiledFn
import app.numlab.domain.parser.ExprParser
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** f(x), with domain errors and non-finite values mapped to NaN. */
fun CompiledFn.safe(x: Double): Double =
    try {
        eval(x).takeIf { it.isFinite() } ?: Double.NaN
    } catch (_: DomainException) {
        Double.NaN
    }

/** "Suggest interval": sign changes on [lo, hi], poles skipped, widened to integers when possible. */
fun findBrackets(
    f: CompiledFn,
    lo: Double = -10.0,
    hi: Double = 10.0,
    steps: Int = 400,
    max: Int = 4,
): List<Pair<Double, Double>> {
    val out = mutableListOf<Pair<Double, Double>>()
    val h = (hi - lo) / steps
    var px = lo
    var py = f.safe(px)
    var i = 1
    while (i <= steps && out.size < max) {
        val x = lo + i * h
        val y = f.safe(x)
        if (py.isFinite() && y.isFinite() && py * y < 0) {
            var lo2 = px
            var hi2 = x
            var flo = py
            var c = lo2
            for (k in 0 until 45) {
                c = (lo2 + hi2) / 2
                val fc = f.safe(c)
                if (!fc.isFinite()) break
                if (flo * fc < 0) hi2 = c else { lo2 = c; flo = fc }
            }
            val fend = f.safe(c)
            val pole = !fend.isFinite() || abs(fend) > 1e-4
            if (!pole) {
                var a = floor(px)
                var b = ceil(x)
                if (a == b) b = a + 1
                if (!(f.safe(a) * f.safe(b) < 0)) {
                    a = toFixed(px, 4).toDouble()
                    b = toFixed(x, 4).toDouble()
                }
                if (out.none { it.first == a && it.second == b }) out += a to b
            }
        }
        px = x
        py = y
        i++
    }
    return out
}

/** Per-iteration explanation: (title, formula) lines plus a stop note on the last row. */
data class Working(val lines: List<Pair<String, String>>, val note: String?)

/** Port of `explainStep` in math.js. */
fun explainStep(res: RootResult, idx: Int): Working {
    val r = res.rows[idx]
    val d = 6
    val f = res.f
    val lines = mutableListOf<Pair<String, String>>()
    when {
        res.method == RootMethod.BISECTION -> {
            val prod = f.eval(r.lo!!) * r.fx
            lines += "Midpoint" to "c = (a + b) / 2 = (${fmt(r.lo, d)} + ${fmt(r.hi, d)}) / 2 = ${fmt(r.x, d)}"
            lines += "Evaluate" to "f(c) = ${fmt(r.fx, d)}"
            lines += "Keep the half with the sign change" to
                if (r.fx == 0.0) {
                    "f(c) = 0, so c is an exact root"
                } else {
                    "f(a)·f(c) = ${fmtE(prod)} ${if (prod < 0) "< 0, so b ← c" else "> 0, so a ← c"}"
                }
        }
        res.method == RootMethod.NEWTON && idx == 0 -> lines += "Starting guess" to "x₀ = ${fmt(r.x, d)}"
        res.method == RootMethod.NEWTON -> {
            val p = res.rows[idx - 1]
            val dv = deriv(f, p.x)
            lines += "Slope by central difference" to "f′(xₙ₋₁) ≈ (f(x+h) − f(x−h)) / 2h = ${fmt(dv, d)}"
            lines += "Newton step" to "xₙ = xₙ₋₁ − f(xₙ₋₁) / f′(xₙ₋₁) = ${fmt(p.x, d)} − " +
                "(${fmt(p.fx, d)}) / (${fmt(dv, d)}) = ${fmt(r.x, d)}"
        }
        idx < 2 -> lines += "Starting guess" to "x${if (idx == 0) "₀" else "₁"} = ${fmt(r.x, d)}"
        else -> {
            val p1 = res.rows[idx - 1]
            val p0 = res.rows[idx - 2]
            lines += "Secant step" to "xₙ = xₙ₋₁ − f(xₙ₋₁)(xₙ₋₁ − xₙ₋₂) / (f(xₙ₋₁) − f(xₙ₋₂))"
            lines += "With numbers" to "${fmt(p1.x, d)} − (${fmt(p1.fx, d)})(${fmt(p1.x - p0.x, d)}) / " +
                "(${fmt(p1.fx - p0.fx, d)}) = ${fmt(r.x, d)}"
        }
    }
    if (r.abs != null) lines += "Change" to "|xₙ − xₙ₋₁| = ${fmtE(r.abs)}"
    val note = when {
        idx != res.rows.lastIndex -> null
        res.converged -> "Stopped here because the change ${fmtE(r.abs)} is below the tolerance ${jsNum(res.tol)}."
        else -> "Stopped at the iteration limit before the change dropped below the tolerance."
    }
    return Working(lines, note)
}

/** One method's run inside [compareRoots]; [evals] counts f(x) calls. */
data class MethodRun(val result: Result<RootResult>, val evals: Int)

/** Runs every method on the same input and counts function evaluations. */
fun compareRoots(inp: RootInput): Result<Map<RootMethod, MethodRun>> = domainCatching {
    val f0 = ExprParser.compileOrThrow(inp.fx)
    RootMethod.entries.associateWith { method ->
        var evals = 0
        val counted = CompiledFn { x -> evals++; f0.eval(x) }
        val res = domainCatching { solveRoot(inp.copy(method = method), counted) }
        MethodRun(res, evals)
    }
}
