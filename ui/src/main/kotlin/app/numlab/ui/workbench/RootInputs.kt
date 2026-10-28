package app.numlab.ui.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.numlab.domain.format.Fmt
import app.numlab.domain.roots.MAX_ITER_CAP
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import app.numlab.ui.R
import app.numlab.ui.components.FxInput
import app.numlab.ui.components.Pill
import app.numlab.ui.components.SectionLabel
import app.numlab.ui.components.Seg
import app.numlab.ui.components.Stepper

/** Roots inputs: method, f(x), examples, suggested brackets, start values, tolerance, limits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RootInputs(
    inp: RootInput,
    brackets: List<Pair<Double, Double>>,
    decimals: Int,
    onChange: ((RootInput) -> RootInput) -> Unit,
    onPreset: (RootPreset) -> Unit,
    onBracket: (Double, Double) -> Unit,
    onDecimals: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Seg(
            listOf(RootMethod.BISECTION to stringResource(R.string.method_bisection),
                RootMethod.NEWTON to stringResource(R.string.method_newton),
                RootMethod.SECANT to stringResource(R.string.method_secant)),
            inp.method, { m -> onChange { it.copy(method = m) } },
        )
        FxInput(inp.fx, { v -> onChange { it.copy(fx = v) } })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.examples))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ROOT_PRESETS.forEach { p -> Pill(p.label, { onPreset(p) }, active = inp.fx == p.fx) }
            }
        }
        AnimatedVisibility(brackets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.roots_detected))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brackets.forEach { (a, b) ->
                        val active = inp.a.toDoubleOrNull() == a && inp.b.toDoubleOrNull() == b
                        Pill("[${Fmt.jsNum(a)}, ${Fmt.jsNum(b)}]", { onBracket(a, b) }, active = active, icon = Icons.Outlined.Bolt)
                    }
                }
            }
        }
        StepperGrid {
            when (inp.method) {
                RootMethod.BISECTION -> {
                    item { NumStepper(stringResource(R.string.field_a), inp.a) { v -> onChange { it.copy(a = v) } } }
                    item { NumStepper(stringResource(R.string.field_b), inp.b) { v -> onChange { it.copy(b = v) } } }
                }
                RootMethod.NEWTON -> wide { NumStepper(stringResource(R.string.field_x0_start), inp.x0) { v -> onChange { it.copy(x0 = v) } } }
                RootMethod.SECANT -> {
                    item { NumStepper(stringResource(R.string.field_x0), inp.s0) { v -> onChange { it.copy(s0 = v) } } }
                    item { NumStepper(stringResource(R.string.field_x1), inp.s1) { v -> onChange { it.copy(s1 = v) } } }
                }
            }
            val e = tolExp(inp.tol)
            item {
                Stepper(stringResource(R.string.field_tolerance), "10${superscript(-e)}",
                    { onChange { it.copy(tol = "1e-${minOf(12, e + 1)}") } }, { onChange { it.copy(tol = "1e-${maxOf(1, e - 1)}") } })
            }
            item {
                val n = inp.maxIter.toIntOrNull()
                Stepper(stringResource(R.string.field_max_iter), inp.maxIter,
                    { onChange { it.copy(maxIter = ((n ?: 110) - 10).coerceAtLeast(5).toString()) } },
                    { onChange { it.copy(maxIter = ((n ?: 90) + 10).coerceAtMost(MAX_ITER_CAP).toString()) } },
                    onText = { v -> onChange { it.copy(maxIter = v) } })
            }
            wide {
                Stepper(stringResource(R.string.field_decimals), decimals.toString(),
                    { onDecimals(decimals - 1) }, { onDecimals(decimals + 1) })
            }
        }
    }
}

/** Editable number with −/+ 0.1 (prototype `NumField`). */
@Composable
fun NumStepper(label: String, value: String, step: Double = 0.1, onChange: (String) -> Unit) {
    val v = value.trim().toDoubleOrNull()
    fun bump(d: Int) {
        if (v != null && v.isFinite()) onChange(Fmt.jsNum(Fmt.toFixed(v + d * step, 10).toDouble()))
    }
    Stepper(label, value, { bump(-1) }, { bump(1) }, onText = onChange,
        invalid = value.isNotBlank() && (v == null || !v.isFinite()))
}

/** Two-column grid that reflows to one column at large font scales (PLAN §6.7). */
class GridScope {
    val cells = mutableListOf<Pair<Boolean, @Composable () -> Unit>>()
    fun item(c: @Composable () -> Unit) { cells += false to c }
    fun wide(c: @Composable () -> Unit) { cells += true to c }
}

@Composable
fun StepperGrid(content: GridScope.() -> Unit) {
    val g = GridScope().apply(content)
    val oneColumn = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.5f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var i = 0
        while (i < g.cells.size) {
            val (wide, c) = g.cells[i]
            if (wide || oneColumn || i + 1 >= g.cells.size || g.cells[i + 1].first) {
                Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { c() } }
                i++
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { c() }
                    Column(Modifier.weight(1f)) { g.cells[i + 1].second() }
                }
                i += 2
            }
        }
    }
}
