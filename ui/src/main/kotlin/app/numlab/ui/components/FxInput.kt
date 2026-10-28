package app.numlab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.parser.ExprParser
import app.numlab.ui.R
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme

// (label, inserted text, wraps as a function call)
private val KEYS = listOf(
    Triple("x", "x", false), Triple("^", "^", false), Triple("√", "sqrt", true), Triple("sin", "sin", true),
    Triple("cos", "cos", true), Triple("tan", "tan", true), Triple("e", "e", false), Triple("π", "pi", false),
    Triple("ln", "ln", true), Triple("exp", "exp", true), Triple("abs", "abs", true), Triple("(", "(", false),
    Triple(")", ")", false),
)

/**
 * The `f(x) =` field with an insert-key row. Shows syntax errors inline; domain errors (which
 * depend on x) are left to the result area. No error state while empty.
 */
@Composable
fun FxInput(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, keys: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length))
    val error = if (value.isBlank()) null else ExprParser.compile(value).exceptionOrNull()?.message
    val focus = LocalFocusManager.current
    val update = { v: TextFieldValue -> tfv = v; if (v.text != value) onChange(v.text) }
    val label = stringResource(R.string.fx_label)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(cs.surfaceVariant, RoundedCornerShape(16.dp))
                .then(if (error != null) Modifier.border(2.dp, cs.error, RoundedCornerShape(16.dp)) else Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("f(x) =", style = MonoStyle.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.fx_placeholder), style = MonoStyle.copy(fontSize = 16.sp),
                        color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    tfv, update, Modifier.fillMaxWidth().semantics { contentDescription = label },
                    textStyle = MonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = cs.onSurface),
                    singleLine = true, cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done,
                        autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
            }
            if (value.isNotBlank()) {
                if (error != null) Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = cs.error)
                else Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = NumLabTheme.colors.good)
            }
        }
        if (error != null) {
            Text(error, Modifier.padding(horizontal = 4.dp), color = cs.error, style = MaterialTheme.typography.bodySmall)
        }
        if (keys) KeyRow(tfv, update)
    }
}

@Composable
private fun KeyRow(tfv: TextFieldValue, update: (TextFieldValue) -> Unit) {
    val tick = rememberTick()
    fun put(t: String, fn: Boolean) {
        val s = tfv.selection.min
        val e = tfv.selection.max
        val text = tfv.text
        val inner = text.substring(s, e)
        val (ins, cursor) = if (fn) {
            "$t($inner)" to if (inner.isEmpty()) s + t.length + 1 else s + t.length + 2 + inner.length
        } else {
            t to s + t.length
        }
        update(TextFieldValue(text.substring(0, s) + ins + text.substring(e), TextRange(cursor)))
    }
    fun del() {
        val s = tfv.selection.min
        val e = tfv.selection.max
        val from = if (s == e) maxOf(0, s - 1) else s
        update(TextFieldValue(tfv.text.substring(0, from) + tfv.text.substring(e), TextRange(from)))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KEYS.forEach { (l, t, fn) -> Key({ tick(); put(t, fn) }) { Text(l, style = MonoStyle.copy(fontSize = 14.sp)) } }
        Key({ tick(); del() }) { Icon(Icons.Outlined.Backspace, stringResource(R.string.cd_backspace), Modifier.size(18.dp)) }
    }
}

@Composable
private fun Key(onClick: () -> Unit, content: @Composable () -> Unit) = Surface(
    onClick = onClick, modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp), shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface,
) { Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { content() } }
