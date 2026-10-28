package app.numlab.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.ui.motion.LocalReduceMotion
import app.numlab.ui.motion.Motion
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme

/** Whether haptics are enabled (settings). */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Light tick; respects the haptics setting. */
@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalHapticsEnabled.current
    return { if (on) h.performHapticFeedback(HapticFeedbackType.SegmentTick) }
}

/** Confirm haptic; respects the haptics setting. */
@Composable
fun rememberConfirm(): () -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalHapticsEnabled.current
    return { if (on) h.performHapticFeedback(HapticFeedbackType.Confirm) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
)

/** Segmented control with a sliding thumb (prototype `Seg2`). */
@Composable
fun <T> Seg(
    options: List<Pair<T, String>>,
    value: T,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumb: Color = MaterialTheme.colorScheme.surface,
) {
    val i = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    val tick = rememberTick()
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val w = with(density) { (width / options.size).toDp() }
    val x by animateDpAsState(w * i, if (LocalReduceMotion.current) tween(0) else tween(300, easing = Motion.Spring), label = "thumb")
    Box(modifier.fillMaxWidth().background(track, CircleShape).padding(4.dp).height(IntrinsicSize.Min)
        .onSizeChanged { width = it.width }) {
        Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(w).fillMaxHeight().background(thumb, CircleShape))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach { (v, label) ->
                Box(
                    Modifier.weight(1f).heightIn(min = 40.dp).selectable(v == value, role = Role.RadioButton) {
                        if (v != value) { tick(); onChange(v) }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, Modifier.padding(horizontal = 4.dp), maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                        color = if (v == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Small rounded chip; `active` inverts colours. */
@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 40.dp), shape = CircleShape,
        color = if (active) cs.onSurface else cs.surfaceVariant, contentColor = if (active) cs.surface else cs.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.size(15.dp).padding(end = 2.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** Round icon button with a content description (≥ 48 dp target). */
@Composable
fun RoundBtn(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surface,
) = IconButton(
    onClick, modifier.size(48.dp), enabled,
    colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = container.copy(alpha = container.alpha * 0.5f),
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
) { Icon(icon, label, Modifier.size(20.dp)) }

/** −/+ box around a value (prototype `Stepper` and `NumField`). Pass [onText] to make it editable. */
@Composable
fun Stepper(
    label: String,
    display: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onText: ((String) -> Unit)? = null,
    invalid: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val tick = rememberTick()
    Row(
        modifier.heightIn(min = 56.dp).background(cs.surfaceVariant, RoundedCornerShape(16.dp))
            .then(if (invalid) Modifier.border(2.dp, cs.error, RoundedCornerShape(16.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton({ tick(); onMinus() }) { Icon(Icons.Outlined.Remove, "Decrease $label", tint = cs.onSurfaceVariant) }
        Column(Modifier.weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = 0.8.sp)
            val style = MonoStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface,
                textAlign = TextAlign.Center)
            if (onText == null) {
                Text(display, style = style)
            } else {
                BasicTextField(
                    display, onText, Modifier.fillMaxWidth().semantics { contentDescription = label }, textStyle = style,
                    singleLine = true, cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next,
                        autoCorrectEnabled = false),
                )
            }
        }
        IconButton({ tick(); onPlus() }) { Icon(Icons.Outlined.Add, "Increase $label", tint = cs.onSurfaceVariant) }
    }
}

enum class BadgeKind { OK, WARN, BAD, NEUTRAL }

@Composable
fun Badge(text: String, kind: BadgeKind, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = NumLabTheme.colors
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        BadgeKind.OK -> c.goodBg to c.good
        BadgeKind.WARN -> c.warnBg to c.warn
        BadgeKind.BAD -> c.badBg to c.bad
        BadgeKind.NEUTRAL -> cs.surfaceVariant to cs.onSurfaceVariant
    }
    Row(modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Inline domain error, never a toast (PLAN §6.2). */
@Composable
fun ErrorBox(message: String, modifier: Modifier = Modifier) {
    val c = NumLabTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.badBg, RoundedCornerShape(16.dp)).padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = c.bad)
        Text(message, color = c.bad, style = MaterialTheme.typography.bodyMedium)
    }
}
