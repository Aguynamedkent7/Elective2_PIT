package app.numlab.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.Truth
import app.numlab.domain.interp.parsePoints
import app.numlab.ui.R
import app.numlab.ui.components.Badge
import app.numlab.ui.components.BadgeKind
import app.numlab.ui.components.FxInput
import app.numlab.ui.components.Pill
import app.numlab.ui.components.SectionLabel
import app.numlab.ui.components.Seg
import app.numlab.ui.theme.MonoStyle

private const val MANY_POINTS = 30

/** Interpolation inputs: method, examples, rows or paste, xₑ and the ground truth (PLAN §6.2). */
@Composable
fun InterpInputs(inp: InterpInput, onChange: ((InterpInput) -> InterpInput) -> Unit) {
    var paste by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Seg(listOf(InterpMethod.LAGRANGE to stringResource(R.string.method_lagrange),
            InterpMethod.NEWTON_DD to stringResource(R.string.method_newton_dd)),
            inp.method, { m -> onChange { it.copy(method = m) } })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.examples))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTERP_EXAMPLES.forEach { (label, ex) -> Pill(stringResource(label), { text = ""; onChange { ex } }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.data_points), Modifier.weight(1f))
                Pill(stringResource(R.string.rows), { paste = false }, active = !paste)
                Pill(stringResource(R.string.paste), { paste = true }, Modifier.padding(start = 6.dp), active = paste)
            }
            if (paste) {
                PasteBox(text) { t ->
                    text = t
                    val p = parsePoints(t)
                    if (p.rows.isNotEmpty()) onChange { it.copy(rows = p.rows) }
                }
            } else {
                PointRows(inp.rows) { rows -> onChange { it.copy(rows = rows) } }
            }
            val n = inp.rows.count { it.x.isNotBlank() }
            if (n >= MANY_POINTS) Badge(stringResource(R.string.many_points_warning, n), BadgeKind.WARN, icon = Icons.Outlined.WarningAmber)
        }
        NumStepper(stringResource(R.string.evaluate_at), inp.xEval) { v -> onChange { it.copy(xEval = v) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(stringResource(R.string.compare_exact))
            Seg(listOf(Truth.NONE to stringResource(R.string.truth_none), Truth.FN to stringResource(R.string.truth_fn),
                Truth.VALUE to stringResource(R.string.truth_value)), inp.truth, { t -> onChange { it.copy(truth = t) } })
            when (inp.truth) {
                Truth.FN -> FxInput(inp.fx, { v -> onChange { it.copy(fx = v) } }, keys = false)
                Truth.VALUE -> NumStepper(stringResource(R.string.y_true), inp.yTrue, step = 0.01) { v -> onChange { it.copy(yTrue = v) } }
                Truth.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PasteBox(text: String, onText: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val parsed = parsePoints(text)
    val label = stringResource(R.string.paste)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).background(cs.surfaceVariant, RoundedCornerShape(16.dp)).padding(14.dp)) {
            if (text.isEmpty()) Text(stringResource(R.string.paste_placeholder), style = MonoStyle.copy(fontSize = 14.sp), color = cs.onSurfaceVariant.copy(alpha = 0.6f))
            BasicTextField(text, onText, Modifier.fillMaxWidth().semantics { contentDescription = label },
                textStyle = MonoStyle.copy(fontSize = 14.sp, lineHeight = 24.sp, color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false))
        }
        val bad = parsed.odd || parsed.rows.size < 2
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (bad) Icons.Outlined.WarningAmber else Icons.Outlined.Check, null, Modifier.size(14.dp),
                tint = if (parsed.odd) cs.error else cs.onSurfaceVariant)
            Text(
                when {
                    parsed.odd -> stringResource(R.string.paste_odd)
                    parsed.rows.size < 2 -> stringResource(R.string.paste_hint)
                    else -> stringResource(R.string.paste_ok, parsed.rows.size)
                },
                style = MaterialTheme.typography.bodySmall, color = if (parsed.odd) cs.error else cs.onSurfaceVariant,
            )
        }
    }
}

/** Editable x, y rows; Enter moves to the next cell. */
@Composable
private fun PointRows(rows: List<PointRow>, onRows: (List<PointRow>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("x", Modifier.weight(1f))
            SectionLabel("y", Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        rows.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(r.x to "x$i", r.y to "y$i").forEachIndexed { k, (v, label) ->
                    BasicTextField(
                        v, { nv -> onRows(rows.mapIndexed { j, row -> if (j != i) row else if (k == 0) row.copy(x = nv) else row.copy(y = nv) }) },
                        Modifier.weight(1f).heightIn(min = 48.dp).background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 13.dp).semantics { contentDescription = label },
                        textStyle = MonoStyle.copy(fontSize = 14.sp, color = cs.onSurface), singleLine = true,
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                    )
                }
                IconButton({ onRows(rows.filterIndexed { j, _ -> j != i }) }, enabled = rows.size > 2) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.cd_remove_point, i), tint = cs.onSurfaceVariant)
                }
            }
        }
        Pill(stringResource(R.string.add_point), { onRows(rows + PointRow("", "")) }, icon = Icons.Outlined.Add)
    }
}
