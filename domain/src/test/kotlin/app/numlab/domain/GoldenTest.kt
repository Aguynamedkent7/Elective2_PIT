package app.numlab.domain

import app.numlab.domain.format.Fmt
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.NodeKind
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.Truth
import app.numlab.domain.interp.errorByN
import app.numlab.domain.interp.genNodes
import app.numlab.domain.interp.interpolate
import app.numlab.domain.interp.parsePoints
import app.numlab.domain.model.DomainException
import app.numlab.domain.model.domainCatching
import app.numlab.domain.parser.ExprParser
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.compareRoots
import app.numlab.domain.roots.findBrackets
import app.numlab.domain.roots.solveRoot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.math.abs
import kotlin.math.max

/** Every case in docs/reference/golden.json (copied to test resources). */
class GoldenTest {
    private val golden: JsonObject = Json.parseToJsonElement(
        javaClass.getResource("/golden.json")!!.readText(),
    ).jsonObject

    private fun cases(key: String) = golden.getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content
    private fun JsonElement.dbl(): Double? = if (this is JsonNull) null else jsonPrimitive.double

    private fun near(expected: Double?, actual: Double?, rel: Double, what: String) {
        if (expected == null || actual == null) return assertEquals(expected, actual, what)
        val ok = abs(expected - actual) <= rel * max(abs(expected), abs(actual)) || abs(expected - actual) < 1e-300
        assertTrue(ok, "$what: expected $expected, got $actual")
    }

    private fun near(expected: JsonElement, actual: Double?, what: String, rel: Double = 1e-9) =
        near(expected.dbl(), actual, rel, what)

    @TestFactory
    fun roots(): List<DynamicTest> = cases("roots").map { c ->
        dynamicTest("roots: ${c.str("name")}") {
            val i = c.getValue("input").jsonObject
            val input = RootInput(
                RootMethod.valueOf(i.str("method").uppercase()), i.str("fx"), i.str("a"), i.str("b"),
                i.str("x0"), i.str("s0"), i.str("s1"), i.str("tol"), i.str("maxIter"),
            )
            val exp = c.getValue("expected").jsonObject
            val res = domainCatching { solveRoot(input) }
            if ("error" in exp) {
                assertEquals(exp.str("error"), res.exceptionOrNull()?.message)
                return@dynamicTest
            }
            val r = res.getOrThrow()
            near(exp.getValue("root"), r.root, "root")
            near(exp.getValue("fRoot"), r.fRoot, "fRoot")
            assertEquals(exp.getValue("iterations").jsonPrimitive.int, r.iterations)
            assertEquals(exp.getValue("converged").jsonPrimitive.boolean, r.converged)
            near(exp.getValue("finalAbs"), r.finalAbs, "finalAbs")
            val rows = exp.getValue("rows").jsonArray.map { it.jsonObject }
            assertEquals(rows.size, r.rows.size, "row count")
            rows.zip(r.rows).forEach { (e, a) ->
                assertEquals(e.getValue("n").jsonPrimitive.int, a.n)
                near(e.getValue("x"), a.x, "x[${a.n}]")
                near(e.getValue("fx"), a.fx, "fx[${a.n}]")
                near(e.getValue("abs"), a.abs, "abs[${a.n}]")
                near(e.getValue("rel"), a.rel, "rel[${a.n}]")
                e["lo"]?.let { near(it, a.lo, "lo[${a.n}]") }
                e["hi"]?.let { near(it, a.hi, "hi[${a.n}]") }
            }
        }
    }

    @TestFactory
    fun compareRoots(): List<DynamicTest> = cases("compareRoots").map { c ->
        val i = c.getValue("input").jsonObject
        dynamicTest("compareRoots: ${i.str("fx")}") {
            val input = RootInput(
                RootMethod.BISECTION, i.str("fx"), i.str("a"), i.str("b"),
                i.str("x0"), i.str("s0"), i.str("s1"), i.str("tol"), i.str("maxIter"),
            )
            val runs = compareRoots(input).getOrThrow()
            for (m in RootMethod.entries) {
                val key = m.name.lowercase()
                assertEquals(c.getValue("evals").jsonObject.getValue(key).jsonPrimitive.int, runs.getValue(m).evals, key)
                val it = c.getValue("iterations").jsonObject.getValue(key)
                assertEquals(if (it is JsonNull) null else it.jsonPrimitive.int, runs.getValue(m).result.getOrNull()?.iterations)
            }
        }
    }

    @TestFactory
    fun interpolation(): List<DynamicTest> = cases("interpolation").map { c ->
        dynamicTest("interp: ${c.str("name")}") {
            val i = c.getValue("input").jsonObject
            val input = InterpInput(
                method = if (i.str("method") == "lagrange") InterpMethod.LAGRANGE else InterpMethod.NEWTON_DD,
                rows = i.getValue("rows").jsonArray.map { PointRow(it.jsonObject.str("x"), it.jsonObject.str("y")) },
                xEval = i.str("xEval"),
                truth = Truth.valueOf(i.str("truth").uppercase()),
                fx = i.str("fx"),
                yTrue = i.str("yTrue"),
            )
            val exp = c.getValue("expected").jsonObject
            val res = domainCatching { interpolate(input) }
            if ("error" in exp) {
                assertEquals(exp.str("error"), res.exceptionOrNull()?.message)
                return@dynamicTest
            }
            val r = res.getOrThrow()
            assertEquals(exp.getValue("n").jsonPrimitive.int, r.n)
            near(exp.getValue("Pxe"), r.pxe, "Pxe")
            val coeffs = exp.getValue("coeffs").jsonArray
            assertEquals(coeffs.size, r.coeffs.size, "coeff count")
            coeffs.forEachIndexed { k, e -> near(e, r.coeffs[k], "c$k") }
            assertEquals(exp.str("poly"), r.poly)
            assertEquals(exp.str("polyForm"), r.polyForm)
            exp.getValue("dd").jsonArray.forEachIndexed { j, col ->
                col.jsonArray.forEachIndexed { k, e -> near(e, r.dd[j][k], "dd[$j][$k]") }
            }
            val basis = exp.getValue("basisAtXe")
            if (basis is JsonArray) {
                basis.map { it.jsonObject }.zip(r.basis!!).forEach { (e, a) ->
                    assertEquals(e.getValue("i").jsonPrimitive.int, a.i)
                    near(e.getValue("L"), a.l, "L${a.i}")
                    near(e.getValue("term"), a.term, "term${a.i}")
                }
            } else {
                assertEquals(null, r.basis)
            }
            val m = exp.getValue("metrics")
            if (m is JsonObject) {
                val a = r.metrics!!
                near(m.getValue("yTrue"), a.yTrue, "yTrue")
                near(m.getValue("abs"), a.abs, "abs")
                near(m.getValue("rel"), a.rel, "rel")
                near(m.getValue("absRel"), a.absRel, "absRel")
                val b = m.getValue("bound")
                if (b is JsonObject) {
                    val ab = a.bound!!
                    near(b.getValue("M"), ab.m, "M", rel = 1e-6)
                    near(b.getValue("fact"), ab.fact, "fact")
                    near(b.getValue("prod"), ab.prod, "prod")
                    near(b.getValue("value"), ab.value, "bound", rel = 1e-6)
                    assertEquals(b.getValue("k").jsonPrimitive.int, ab.k)
                    assertEquals(b.getValue("extrapolating").jsonPrimitive.boolean, ab.extrapolating)
                } else {
                    assertEquals(null, a.bound)
                }
            } else {
                assertEquals(null, r.metrics)
            }
            exp.getValue("sample").jsonArray.map { it.jsonObject }.forEach { s ->
                val x = s.getValue("x").jsonPrimitive.double
                near(s.getValue("P"), r.p(x), "P($x)")
            }
        }
    }

    @TestFactory
    fun parser(): List<DynamicTest> = cases("parser").map { c ->
        dynamicTest("parser: ${c.str("expr")} @ ${c.str("x")}") {
            val x = c.getValue("x").jsonPrimitive.double
            val out = try {
                Result.success(ExprParser.compileOrThrow(c.str("expr")).eval(x))
            } catch (e: DomainException) {
                Result.failure(e)
            }
            if ("error" in c) assertEquals(c.str("error"), out.exceptionOrNull()?.message)
            else near(c.getValue("value"), out.getOrThrow(), "value")
        }
    }

    @TestFactory
    fun brackets(): List<DynamicTest> = cases("brackets").map { c ->
        dynamicTest("brackets: ${c.str("fx")}") {
            val expected = c.getValue("brackets").jsonArray.map {
                it.jsonArray[0].jsonPrimitive.double to it.jsonArray[1].jsonPrimitive.double
            }
            assertEquals(expected, findBrackets(ExprParser.compileOrThrow(c.str("fx"))))
        }
    }

    @TestFactory
    fun parsePoints(): List<DynamicTest> = cases("parsePoints").map { c ->
        dynamicTest("parsePoints: ${c.str("text").replace("\n", "⏎")}") {
            val p = parsePoints(c.str("text"))
            val rows = c.getValue("rows").jsonArray.map {
                PointRow(it.jsonArray[0].jsonPrimitive.content, it.jsonArray[1].jsonPrimitive.content)
            }
            assertEquals(rows, p.rows)
            assertEquals(c.getValue("odd").jsonPrimitive.boolean, p.odd)
            assertEquals(c.getValue("count").jsonPrimitive.int, p.count)
        }
    }

    @TestFactory
    fun errorByNodeCount(): List<DynamicTest> {
        val got = errorByN(ExprParser.compileOrThrow("1/(1+25x^2)"), -1.0, 1.0, 2, 12)
        return cases("errorByNodeCount_runge").mapIndexed { idx, c ->
            dynamicTest("errorByN: n=${c.str("n")}") {
                assertEquals(c.getValue("n").jsonPrimitive.int, got[idx].n)
                near(c.getValue("equal"), got[idx].equal, "equal")
                near(c.getValue("cheb"), got[idx].cheb, "cheb")
            }
        }
    }

    @TestFactory
    fun formatting(): List<DynamicTest> = cases("formatting").map { c ->
        dynamicTest("format: ${c.str("v")}") {
            val v = c.str("v").let { if (it == "NaN") Double.NaN else it.toDouble() }
            assertEquals(c.str("fmt6"), Fmt.fmt(v, 6))
            assertEquals(c.str("fmtE"), Fmt.fmtE(v))
        }
    }

    @TestFactory
    fun rungePresetMatchesGenNodes(): List<DynamicTest> = listOf(
        dynamicTest("genNodes: Runge 7 equal nodes") {
            val runge = cases("interpolation").first { it.str("name").startsWith("runge") }
            val expected = runge.getValue("input").jsonObject.getValue("rows").jsonArray
                .map { PointRow(it.jsonObject.str("x"), it.jsonObject.str("y")) }
            assertEquals(expected, genNodes(ExprParser.compileOrThrow("1/(1+25x^2)"), -1.0, 1.0, 7, NodeKind.EQUAL))
        },
    )
}
