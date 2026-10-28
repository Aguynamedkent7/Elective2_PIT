package app.numlab.domain

import app.numlab.domain.export.toCsv
import app.numlab.domain.export.toLatex
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.interpolate
import app.numlab.domain.model.DomainException
import app.numlab.domain.parser.ExprParser
import app.numlab.domain.roots.RootFailure
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.explainStep
import app.numlab.domain.roots.solveRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class PropertyTest {
    private val rnd = Random(42)

    @Test
    fun `all three methods find the single real root of random cubics`() {
        repeat(300) {
            val r = rnd.nextDouble(-5.0, 5.0)
            val c = rnd.nextDouble(1.0, 10.0)
            // (x − r)(x² + c) has exactly one real root, r.
            val fx = "(x - ($r))*(x^2 + $c)"
            val base = RootInput(
                RootMethod.BISECTION, fx, a = "${r - rnd.nextDouble(0.5, 3.0)}", b = "${r + rnd.nextDouble(0.5, 3.0)}",
                x0 = "${r + 0.3}", s0 = "${r - 0.4}", s1 = "${r + 0.4}", tol = "1e-10", maxIter = "200",
            )
            for (m in RootMethod.entries) {
                val res = solveRoot(base.copy(method = m))
                assertTrue(res.converged, "$m on $fx")
                assertTrue(abs(res.root - r) < 1e-7, "$m on $fx: ${res.root} vs $r")
            }
        }
    }

    @Test
    fun `interpolating polynomial reproduces nodes and Lagrange agrees with Newton DD`() {
        repeat(300) {
            val n = rnd.nextInt(2, 8)
            val xs = (0 until n).map { it + rnd.nextDouble(-0.3, 0.3) }.shuffled(rnd)
            val ys = xs.map { rnd.nextDouble(-10.0, 10.0) }
            val rows = xs.zip(ys).map { (x, y) -> PointRow(x.toString(), y.toString()) }
            val lag = interpolate(InterpInput(InterpMethod.LAGRANGE, rows, "0.5"))
            val ndd = interpolate(InterpInput(InterpMethod.NEWTON_DD, rows, "0.5"))
            xs.zip(ys).forEach { (x, y) ->
                assertTrue(abs(lag.p(x) - y) < 1e-9 * max(1.0, abs(y)), "Lagrange at $x")
                assertTrue(abs(ndd.p(x) - y) < 1e-9 * max(1.0, abs(y)), "Newton DD at $x")
            }
            assertTrue(abs(lag.pxe - ndd.pxe) < 1e-9 * max(1.0, abs(lag.pxe)))
        }
    }

    @Test
    fun `parser only ever throws DomainException on fuzzed input`() {
        val tokens = listOf(
            "x", "2", "0", "-1.5", "1e3", ".", "e", "pi", "π", "√", "+", "-", "*", "/", "^", "(", ")", ",",
            "sin", "ln", "log2", "sqrt", "asin", "abs", "foo", " ", "×", "÷", "−", "$", "1.2.3", "E",
        )
        repeat(10_000) {
            val src = (0 until rnd.nextInt(0, 12)).joinToString("") { tokens.random(rnd) }
            try {
                val f = ExprParser.compileOrThrow(src)
                for (x in doubleArrayOf(-2.0, 0.0, 0.5, 3.0)) {
                    try {
                        f.eval(x)
                    } catch (_: DomainException) {
                        // expected for domain errors
                    }
                }
            } catch (_: DomainException) {
                // expected for syntax errors
            }
        }
    }

    @Test
    fun `domain error mid-run keeps the rows computed so far`() {
        // f′(0) = 0 for x² + 1: the starting row is kept on the failure.
        val e = runCatching {
            solveRoot(RootInput(RootMethod.NEWTON, "x^2 + 1", x0 = "0"))
        }.exceptionOrNull() as RootFailure
        assertEquals("f′(x) is about 0 at x = 0. Newton cannot continue", e.message)
        assertEquals(1, e.rows.size)
    }

    @Test
    fun `explain step and exports for the default bisection`() {
        val res = solveRoot(RootInput(RootMethod.BISECTION, "x^3 - x - 2", a = "1", b = "2"))
        val first = explainStep(res, 0)
        assertEquals("Midpoint" to "c = (a + b) / 2 = (1.000000 + 2.000000) / 2 = 1.500000", first.lines[0])
        assertEquals(null, first.note)
        val last = explainStep(res, res.rows.lastIndex)
        assertEquals("Stopped here because the change 7.63e-6 is below the tolerance 0.00001.", last.note)
        assertEquals("x^{*} \\approx 1.521385", res.toLatex())
        assertEquals("iter,x,f(x),abs_error,rel_error_pct", res.toCsv().lines()[0])
        assertEquals("1,1.5,-0.125,,", res.toCsv().lines()[1])

        val sin = interpolate(
            InterpInput(
                InterpMethod.LAGRANGE,
                listOf(PointRow("0", "0"), PointRow("1", "0.8415"), PointRow("2", "0.9093"), PointRow("3", "0.1411")),
                "1.5",
            ),
        )
        assertEquals("P(x) = -0.010383x^{3} - 0.3557x^{2} + 1.2076x", sin.toLatex())
    }
}
