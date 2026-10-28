package app.numlab.ui.workbench

import androidx.annotation.StringRes
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.interp.NodeKind
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.Truth
import app.numlab.domain.interp.genNodes
import app.numlab.domain.model.RunInput
import app.numlab.domain.parser.ExprParser
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.RootResult
import app.numlab.domain.roots.RootRow
import app.numlab.ui.R

enum class Module { ROOTS, INTERP }

/** Result of the latest debounced solve. [Err.rows] keeps rows computed before a mid-run error. */
sealed interface Outcome<out T> {
    data object Idle : Outcome<Nothing>
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val message: String, val rows: List<RootRow> = emptyList()) : Outcome<Nothing>
}

/** Why the Graph button is locked (null = unlocked). */
enum class Lock(@StringRes val caption: Int) {
    IDLE_ROOT(R.string.graph_locked_caption),
    IDLE_INTERP(R.string.graph_locked_interp),
    NEED_X(R.string.graph_locked_need_x),
    FIX(R.string.graph_locked_fix),
}

data class WorkbenchState(
    val module: Module = Module.ROOTS,
    val root: RootInput = START_ROOT,
    val interp: InterpInput = START_INTERP,
    val rootOutcome: Outcome<RootResult> = Outcome.Idle,
    /** Syntax error of the current f(x), set synchronously so a stale result never shows (PLAN §8). */
    val rootSyntax: String? = null,
    val interpOutcome: Outcome<InterpResult> = Outcome.Idle,
    val brackets: List<Pair<Double, Double>> = emptyList(),
    val decimals: Int = 6,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val historyCount: Int = 0,
    /** The graph canvas is open (survives rotation and process death). */
    val graphOpen: Boolean = false,
) {
    val lock: Lock?
        get() = when (module) {
            Module.ROOTS -> when {
                root.fx.isBlank() -> Lock.IDLE_ROOT
                rootSyntax != null -> Lock.FIX
                rootOutcome is Outcome.Ok -> null
                else -> Lock.FIX
            }
            Module.INTERP -> when {
                pointCount(interp.rows) < 2 -> Lock.IDLE_INTERP
                interpOutcome is Outcome.Ok -> null
                interp.xEval.isBlank() -> Lock.NEED_X
                else -> Lock.FIX
            }
        }

    /** Error to show in the result area: hidden while idle or only waiting for xₑ. */
    val visibleError: Outcome.Err?
        get() = when (lock) {
            Lock.FIX -> if (module == Module.ROOTS) rootSyntax?.let { Outcome.Err(it) } ?: rootOutcome as? Outcome.Err
            else interpOutcome as? Outcome.Err
            else -> null
        }

    /** Inputs to store in history (interp rows: blank rows dropped). */
    val run: RunInput
        get() = if (module == Module.ROOTS) RunInput.Root(root)
        else RunInput.Interp(interp.copy(rows = interp.rows.filter { it.x.isNotBlank() || it.y.isNotBlank() }))
}

fun pointCount(rows: List<PointRow>) = rows.count {
    it.x.trim().toDoubleOrNull()?.isFinite() == true && it.y.trim().toDoubleOrNull()?.isFinite() == true
}

val START_ROOT = RootInput(RootMethod.BISECTION, "", "1", "2", "1.5", "1", "2")
val BLANK_ROWS = List(3) { PointRow("", "") }
val START_INTERP = InterpInput(InterpMethod.LAGRANGE, BLANK_ROWS, "")

/** Root presets (prototype `PRESETS`): label to fx, a, b, x0, s0, s1. */
data class RootPreset(val label: String, val fx: String, val a: String, val b: String, val x0: String, val s0: String, val s1: String)

val ROOT_PRESETS = listOf(
    RootPreset("x³ − x − 2", "x^3 - x - 2", "1", "2", "1.5", "1", "2"),
    RootPreset("cos x − x", "cos(x) - x", "0", "1", "0.5", "0", "1"),
    RootPreset("eˣ − 3x", "exp(x) - 3x", "0", "1", "0.5", "0", "1"),
    RootPreset("ln x + x − 2", "ln(x) + x - 2", "1", "2", "1.5", "1", "2"),
    RootPreset("x² − 2", "x^2 - 2", "1", "2", "1.5", "1", "2"),
)

private val SIN_ROWS = listOf(PointRow("0", "0"), PointRow("1", "0.8415"), PointRow("2", "0.9093"), PointRow("3", "0.1411"))
private const val RUNGE = "1/(1+25x^2)"

/** Interpolation examples: sin 4 points, eˣ 3 points, Runge 7 equal nodes. */
val INTERP_EXAMPLES: List<Pair<Int, InterpInput>> = listOf(
    R.string.example_sin to InterpInput(InterpMethod.LAGRANGE, SIN_ROWS, "1.5", Truth.FN, "sin(x)"),
    R.string.example_exp to InterpInput(InterpMethod.NEWTON_DD,
        listOf(PointRow("0", "1"), PointRow("1", "2.7183"), PointRow("2", "7.3891")), "1.5", Truth.FN, "exp(x)"),
    R.string.example_runge to InterpInput(InterpMethod.LAGRANGE,
        genNodes(ExprParser.compileOrThrow(RUNGE), -1.0, 1.0, 7, NodeKind.EQUAL), "0.9", Truth.FN, RUNGE),
)

/** Tolerance exponent e for 10⁻ᵉ, clamped to 1..12 (prototype `tolExp`). */
fun tolExp(tol: String): Int {
    val v = tol.trim().toDoubleOrNull()
    return if (v != null && v.isFinite() && v > 0) Math.round(-kotlin.math.log10(v)).toInt().coerceIn(1, 12) else 5
}

private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
fun superscript(n: Int): String = n.toString().map { if (it == '-') '⁻' else SUP[it - '0'] }.joinToString("")
