package app.numlab.domain.roots

import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.model.DomainException
import app.numlab.domain.model.parseNumber
import app.numlab.domain.parser.CompiledFn
import app.numlab.domain.parser.ExprParser
import kotlin.math.abs
import kotlin.math.max

/** Root-finding methods. Adding one = new entry + a branch in [solveRoot] + strings (PLAN §3.3). */
enum class RootMethod { BISECTION, NEWTON, SECANT }

/** Raw inputs as typed by the user; numeric fields stay strings until validated. */
data class RootInput(
    val method: RootMethod,
    val fx: String,
    val a: String = "",
    val b: String = "",
    val x0: String = "",
    val s0: String = "",
    val s1: String = "",
    val tol: String = "1e-5",
    val maxIter: String = "100",
)

/** One iteration row. `abs`/`rel` are null for starting rows; `lo`/`hi` only for bisection. */
data class RootRow(
    val n: Int,
    val x: Double,
    val fx: Double,
    val abs: Double?,
    val rel: Double?,
    val lo: Double? = null,
    val hi: Double? = null,
)

data class RootResult(
    val method: RootMethod,
    val f: CompiledFn,
    val root: Double,
    val fRoot: Double,
    val iterations: Int,
    val converged: Boolean,
    val rows: List<RootRow>,
    val tol: Double,
    val finalAbs: Double?,
    val used: List<Double>,
)

/** A failed run. [rows] holds whatever was computed before the error (PLAN §8). */
class RootFailure(message: String, val rows: List<RootRow>) : DomainException(message)

const val MAX_ITER_CAP = 10_000

/** Central difference with `h = 1e-5·max(1,|x|)`. */
fun deriv(f: CompiledFn, x: Double, h: Double = 1e-5): Double {
    val hh = h * max(1.0, abs(x))
    return (f.eval(x + hh) - f.eval(x - hh)) / (2 * hh)
}

private fun relOf(abs: Double?, x: Double): Double? = if (abs == null || x == 0.0) null else abs / abs(x) * 100

/** Port of `solveRoot` in math.js. Throws [DomainException] (or [RootFailure] mid-run). */
fun solveRoot(inp: RootInput, fn: CompiledFn? = null): RootResult {
    val f = fn ?: ExprParser.compileOrThrow(inp.fx)
    val tol = parseNumber(inp.tol, "tolerance")
    val maxIterD = parseNumber(inp.maxIter, "max iterations")
    if (tol <= 0) throw DomainException("Tolerance must be greater than 0")
    if (maxIterD != Math.rint(maxIterD) || maxIterD < 1) {
        throw DomainException("Max iterations must be a whole number ≥ 1")
    }
    if (maxIterD > MAX_ITER_CAP) throw DomainException("Max iterations must be at most $MAX_ITER_CAP")
    val maxIter = maxIterD.toInt()
    val rows = mutableListOf<RootRow>()
    var converged = false
    val initial: Int
    val used: List<Double>
    try {
        when (inp.method) {
            RootMethod.BISECTION -> {
                var lo = parseNumber(inp.a, "a")
                var hi = parseNumber(inp.b, "b")
                if (lo >= hi) throw DomainException("Need a < b")
                used = listOf(lo, hi)
                initial = 0
                var flo = f.eval(lo)
                val fhi = f.eval(hi)
                if (flo * fhi > 0) throw DomainException("f(a) and f(b) must have opposite signs")
                var prev: Double? = null
                for (i in 1..maxIter) {
                    val x = (lo + hi) / 2
                    val fc = f.eval(x)
                    val abs = prev?.let { abs(x - it) }
                    rows += RootRow(i, x, fc, abs, relOf(abs, x), lo, hi)
                    if (fc == 0.0 || (abs != null && abs < tol)) {
                        converged = true
                        break
                    }
                    if (flo * fc < 0) {
                        hi = x
                    } else {
                        lo = x
                        flo = fc
                    }
                    prev = x
                }
            }
            RootMethod.NEWTON -> {
                var x = parseNumber(inp.x0, "x₀")
                used = listOf(x)
                initial = 1
                var fcur = f.eval(x)
                rows += RootRow(0, x, fcur, null, null)
                for (i in 1..maxIter) {
                    val d = deriv(f, x)
                    if (abs(d) < 1e-12) {
                        throw DomainException("f′(x) is about 0 at x = ${fmt(x, 4)}. Newton cannot continue")
                    }
                    val xn = x - fcur / d
                    val abs = abs(xn - x)
                    val fnx = f.eval(xn)
                    fcur = fnx
                    rows += RootRow(i, xn, fnx, abs, relOf(abs, xn))
                    x = xn
                    if (fnx == 0.0 || abs < tol) {
                        converged = true
                        break
                    }
                }
            }
            RootMethod.SECANT -> {
                var xp = parseNumber(inp.s0, "x₀")
                var x = parseNumber(inp.s1, "x₁")
                if (xp == x) throw DomainException("x₀ and x₁ must differ")
                used = listOf(xp, x)
                initial = 2
                var fp = f.eval(xp)
                var fc = f.eval(x)
                rows += RootRow(0, xp, fp, null, null)
                rows += RootRow(1, x, fc, null, null)
                for (i in 2..maxIter + 1) {
                    val den = fc - fp
                    if (den == 0.0) throw DomainException("f(xₙ) = f(xₙ₋₁): the secant line is flat")
                    val xn = x - fc * (x - xp) / den
                    val abs = abs(xn - x)
                    val fnx = f.eval(xn)
                    rows += RootRow(i, xn, fnx, abs, relOf(abs, xn))
                    xp = x
                    fp = fc
                    x = xn
                    fc = fnx
                    if (fnx == 0.0 || abs < tol) {
                        converged = true
                        break
                    }
                }
            }
        }
    } catch (e: DomainException) {
        throw RootFailure(e.message, rows.toList())
    }
    val last = rows.last()
    return RootResult(
        method = inp.method, f = f, root = last.x, fRoot = last.fx, iterations = rows.size - initial,
        converged = converged, rows = rows, tol = tol, finalAbs = last.abs, used = used,
    )
}
