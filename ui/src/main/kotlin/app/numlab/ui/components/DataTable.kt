package app.numlab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.domain.format.Fmt
import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.format.Fmt.fmtE
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootResult
import app.numlab.domain.roots.RootRow
import app.numlab.ui.theme.MonoStyle
import app.numlab.ui.theme.NumLabTheme

enum class Tone { NONE, GOOD, WARN, ACCENT }

data class Cell(val text: String, val tone: Tone = Tone.NONE)

data class Col(val header: String, val width: Dp, val start: Boolean = false)

/**
 * Zebra table with a sticky header (PLAN §8: lazy, so 10 000 rows are fine).
 * Scrolls horizontally when the columns don't fit.
 */
@Composable
fun DataTable(
    cols: List<Col>,
    rows: List<List<Cell>>,
    modifier: Modifier = Modifier,
    active: Int? = null,
    onRow: ((Int) -> Unit)? = null,
    maxHeight: Dp = 320.dp,
    dense: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberLazyListState()
    LaunchedEffect(active) { if (active != null && active > 0) state.animateScrollToItem(maxOf(0, active - 2)) }
    val pad = if (dense) 6.dp else 9.dp
    BoxWithConstraints(modifier) {
        // Columns stretch to fill wide containers (landscape, tablets); narrower ones scroll.
        val total = cols.fold(0.dp) { a, c -> a + c.width }
        val stretch = if (constraints.hasBoundedWidth && maxWidth > total) maxWidth / total else 1f
        val fit = cols.map { it.copy(width = it.width * stretch) }
        Box(Modifier.clip(RoundedCornerShape(16.dp)).background(cs.surface).horizontalScroll(rememberScrollState())) {
            LazyColumn(Modifier.width(total * stretch).heightIn(max = maxHeight), state) {
                stickyHeader {
                    Row(Modifier.background(cs.surfaceVariant)) {
                        fit.forEach { c ->
                            Text(c.header.uppercase(), Modifier.width(c.width).padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                                textAlign = if (c.start) TextAlign.Start else TextAlign.End)
                        }
                    }
                }
                itemsIndexed(rows) { ri, row ->
                    val bg = when {
                        ri == active -> cs.primary.copy(alpha = 0.16f).compositeOver(cs.surface)
                        ri % 2 == 1 -> cs.surfaceVariant.copy(alpha = 0.55f).compositeOver(cs.surface)
                        else -> cs.surface
                    }
                    Row(Modifier.background(bg).then(if (onRow != null) Modifier.clickable { onRow(ri) } else Modifier),
                        verticalAlignment = Alignment.CenterVertically) {
                        row.forEachIndexed { ci, cell -> CellText(cell, fit[ci], pad, ri == active) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellText(cell: Cell, col: Col, pad: Dp, bold: Boolean) {
    val c = NumLabTheme.colors
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (cell.tone) {
        Tone.GOOD -> c.goodBg to c.good
        Tone.WARN -> c.warnBg to c.warn
        Tone.ACCENT -> cs.primary to cs.onPrimary
        Tone.NONE -> Color.Transparent to if (col.start) cs.onSurfaceVariant else cs.onSurface
    }
    Box(Modifier.width(col.width).padding(horizontal = 8.dp, vertical = pad),
        contentAlignment = if (col.start) Alignment.CenterStart else Alignment.CenterEnd) {
        Text(cell.text, Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = if (cell.tone == Tone.NONE) 0.dp else 6.dp),
            style = MonoStyle.copy(fontSize = 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal), color = fg,
            maxLines = 1)
    }
}

/** Relative-error pill: green once below tolerance, amber while ≥ 1 %. */
private fun relCell(r: RootRow, tol: Double): Cell {
    val rel = r.rel ?: return Cell("—")
    val txt = if (rel >= 1e-3 || rel == 0.0) Fmt.toFixed(rel, 4) else Fmt.toExponential(rel, 2)
    val tone = if (r.abs != null && r.abs!! < tol) Tone.GOOD else if (rel >= 1) Tone.WARN else Tone.NONE
    return Cell(txt, tone)
}

fun rootTable(tol: Double, rows: List<RootRow>, dp: Int): Pair<List<Col>, List<List<Cell>>> =
    listOf(Col("Iter", 52.dp, start = true), Col("xₙ", 112.dp), Col("f(xₙ)", 92.dp), Col("|xₙ − xₙ₋₁|", 104.dp),
        Col("Rel. err %", 104.dp)) to rows.map { r ->
        listOf(Cell(r.n.toString()), Cell(Fmt.toFixed(r.x, maxOf(dp, 4))), Cell(fmtE(r.fx)),
            Cell(r.abs?.let(::fmtE) ?: "—"), relCell(r, tol))
    }

fun basisTable(res: InterpResult, dp: Int): Pair<List<Col>, List<List<Cell>>> =
    listOf(Col("i", 40.dp, start = true), Col("xᵢ", 84.dp), Col("yᵢ", 84.dp), Col("Lᵢ(x)", 104.dp), Col("yᵢ·Lᵢ", 104.dp)) to
        res.basis.orEmpty().map { b ->
            listOf(Cell(b.i.toString()), Cell(fmt(b.x, 4)), Cell(fmt(b.y, 4)), Cell(fmt(b.l, dp)), Cell(fmt(b.term, dp)))
        }

private fun ddName(j: Int) = when (j) { 0 -> "f[xᵢ]"; 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${j}th" }

fun ddTable(res: InterpResult): Pair<List<Col>, List<List<Cell>>> =
    (listOf(Col("xᵢ", 72.dp, start = true)) + res.dd.indices.map { Col(ddName(it), 100.dp) }) to
        res.points.indices.map { i ->
            listOf(Cell(fmt(res.points[i].x, 4))) + res.dd.map { col ->
                if (i < col.size) Cell(fmt(col[i], 5), if (i == 0) Tone.ACCENT else Tone.NONE) else Cell("")
            }
        }
