package app.numlab.domain.export

import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.format.Fmt.jsNum
import app.numlab.domain.format.Fmt.sig5
import app.numlab.domain.format.Fmt.toFixed
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootResult
import kotlin.math.abs

enum class ExportFormat { CSV, LATEX, PLAIN }

/** Root iterations as CSV: `iter,x,f(x),abs_error,rel_error_pct`. */
fun RootResult.toCsv(): String =
    (listOf("iter,x,f(x),abs_error,rel_error_pct") + rows.map { r ->
        listOf(r.n.toString(), jsNum(r.x), jsNum(r.fx), r.abs?.let(::jsNum) ?: "", r.rel?.let(::jsNum) ?: "")
            .joinToString(",")
    }).joinToString("\n")

/** Data points as CSV: `x,y`. */
fun InterpResult.toCsv(): String =
    (listOf("x,y") + points.map { "${jsNum(it.x)},${jsNum(it.y)}" }).joinToString("\n")

fun RootResult.toLatex(): String = "x^{*} \\approx ${toFixed(root, 6)}"

fun InterpResult.toLatex(): String {
    val terms = coeffs.withIndex().reversed().filter { abs(it.value) > 1e-9 }
        .mapIndexed { i, (k, c) ->
            val sign = if (i == 0) (if (c < 0) "-" else "") else if (c < 0) " - " else " + "
            val x = if (k == 0) "" else "x" + (if (k > 1) "^{$k}" else "")
            sign + sig5(abs(c)) + x
        }
    return "P(x) = ${terms.joinToString("").ifEmpty { "0" }}"
}

fun RootResult.toPlain(decimals: Int = 6): String = "x* ≈ ${fmt(root, decimals)}"

fun InterpResult.toPlain(decimals: Int = 6): String = "P(${jsNum(xe)}) = ${fmt(pxe, decimals)}\nP(x) = $poly"
