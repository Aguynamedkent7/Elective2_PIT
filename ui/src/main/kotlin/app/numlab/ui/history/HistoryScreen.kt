package app.numlab.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunInput
import app.numlab.ui.R
import app.numlab.ui.components.MenuRow
import app.numlab.ui.components.Pill
import app.numlab.ui.components.RoundBtn
import app.numlab.ui.components.Solved
import app.numlab.ui.components.csv
import app.numlab.ui.components.latex
import app.numlab.ui.components.rememberExporter
import app.numlab.ui.components.resultText
import app.numlab.ui.motion.riseIn
import app.numlab.ui.theme.Bricolage
import app.numlab.ui.theme.NumLabTheme
import kotlinx.coroutines.launch

/** Where History sends the user. */
class HistoryNav(val back: () -> Unit, val openInputs: (RunInput) -> Unit, val openGraph: (RunInput) -> Unit)

/** "Sample" / "Just now" / "Today" / "Yesterday" / a date. */
@Composable
fun whenLabel(item: HistoryItem): String {
    val now = System.currentTimeMillis()
    return when {
        item.sample -> stringResource(R.string.when_sample)
        now - item.updatedAt < 60_000 -> stringResource(R.string.when_just_now)
        DateUtils.isToday(item.updatedAt) -> stringResource(R.string.when_today)
        DateUtils.isToday(item.updatedAt + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.when_yesterday)
        else -> DateUtils.formatDateTime(LocalContext.current, item.updatedAt, DateUtils.FORMAT_SHOW_DATE)
    }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel, decimals: Int, nav: HistoryNav) {
    val st by vm.state.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val res = LocalResources.current
    val ex = rememberExporter()
    val toast: (Int) -> Unit = { m -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(res.getString(m)) } }
    // Bumped on Undo so a restored card gets a fresh (not dismissed) swipe state under a new key.
    var epoch by remember { mutableIntStateOf(0) }
    val delete: (HistoryItem) -> Unit = { item ->
        vm.delete(item) { removed ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(res.getString(R.string.run_deleted), res.getString(R.string.undo), duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) { epoch++; vm.restore(removed) }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(cs.background)) {
        LazyColumn(
            Modifier.fillMaxSize(), rememberLazyListState(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") { Header(st, vm, nav) }
            if (st.loaded && st.cards.isEmpty()) item(key = "empty") { Empty(st.all == 0) }
            itemsIndexed(st.cards, key = { _, c -> "${c.item.id}/$epoch" }) { i, card ->
                val dismiss = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    dismiss,
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().background(NumLabTheme.colors.bad.copy(alpha = if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) 1f else 0f),
                            RoundedCornerShape(28.dp)).padding(end = 28.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Icons.Outlined.DeleteOutline, null, tint = Color.White)
                        }
                    },
                    modifier = Modifier.widthIn(max = 640.dp).animateItem().riseIn(minOf(i, 6), staggerMs = 40, rise = 16f),
                    enableDismissFromStartToEnd = false,
                    onDismiss = { delete(card.item) },
                ) {
                    HistoryCard(card, whenLabel(card.item), decimals, CardActions(
                        pin = { vm.togglePin(card.item) },
                        open = nav.openInputs,
                        openGraph = nav.openGraph,
                        menu = { solved -> CardMenu(card, solved, nav, vm, delete, toast, ex) },
                    ))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun Header(st: HistoryState, vm: HistoryViewModel, nav: HistoryNav) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().statusBarsPadding().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundBtn(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.cd_close_history), nav.back)
            Text(stringResource(R.string.history_title), Modifier.weight(1f), fontFamily = Bricolage,
                style = MaterialTheme.typography.headlineMedium)
            Text(if (st.all == 1) stringResource(R.string.history_run_one) else stringResource(R.string.history_runs, st.all),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Filter.ALL to R.string.filter_all, Filter.ROOTS to R.string.filter_roots, Filter.INTERP to R.string.filter_interp,
                Filter.PINNED to R.string.filter_pinned).forEach { (f, l) -> Pill(stringResource(l), { vm.setFilter(f) }, active = st.filter == f) }
        }
        Text(stringResource(R.string.history_help), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Empty(noRuns: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().background(cs.surface, RoundedCornerShape(28.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (noRuns) Image(painterResource(R.drawable.ic_logo), null, Modifier.size(72.dp).alpha(0.35f))
        Text(stringResource(if (noRuns) R.string.history_empty else R.string.history_no_match), color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CardMenu(
    card: Card, solved: Solved?, nav: HistoryNav, vm: HistoryViewModel, delete: (HistoryItem) -> Unit, toast: (Int) -> Unit,
    ex: app.numlab.ui.components.Exporter,
) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box {
        IconButton({ open = true }) { Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.cd_more)) }
        DropdownMenu(open, { open = false }) {
            val ok = solved != null
            MenuRow(R.string.menu_open_inputs, Icons.Outlined.Edit) { open = false; nav.openInputs(card.item.run) }
            MenuRow(R.string.menu_open_graph, Icons.AutoMirrored.Outlined.ShowChart, ok) { open = false; nav.openGraph(card.item.run) }
            MenuRow(R.string.menu_duplicate, Icons.Outlined.LibraryAdd) { open = false; vm.duplicate(card.item); toast(R.string.duplicated) }
            MenuRow(R.string.menu_copy_result, Icons.Outlined.ContentCopy, ok) { open = false; ex.copyText(solved!!.resultText(ctx)); toast(R.string.copied_result) }
            MenuRow(R.string.menu_copy_csv, Icons.Outlined.TableChart, ok) { open = false; ex.copyText(solved!!.csv()); toast(R.string.copied_csv) }
            MenuRow(R.string.menu_copy_latex, Icons.AutoMirrored.Outlined.List, ok) { open = false; ex.copyText(solved!!.latex()); toast(R.string.copied_latex) }
            MenuRow(R.string.menu_delete, Icons.Outlined.DeleteOutline, danger = true) { open = false; delete(card.item) }
        }
    }
}
