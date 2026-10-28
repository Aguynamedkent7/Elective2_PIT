package app.numlab.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import app.numlab.domain.export.ExportFormat
import app.numlab.domain.format.Fmt
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.RootResult
import app.numlab.domain.usecase.BuildExportUseCase
import app.numlab.ui.R
import java.io.File
import kotlinx.coroutines.launch

/** A solved run, for export menus. */
sealed interface Solved {
    data class Root(val res: RootResult) : Solved
    data class Interp(val res: InterpResult, val xEval: String) : Solved
}

private val export = BuildExportUseCase()

fun Solved.csv() = when (this) {
    is Solved.Root -> export(res, ExportFormat.CSV)
    is Solved.Interp -> export(res, ExportFormat.CSV)
}

fun Solved.latex() = when (this) {
    is Solved.Root -> export(res, ExportFormat.LATEX)
    is Solved.Interp -> export(res, ExportFormat.LATEX)
}

/** "Copy result" text (prototype `textOf`). */
fun Solved.resultText(ctx: Context): String = when (this) {
    is Solved.Root -> ctx.getString(R.string.result_text_root, Fmt.fmt(res.root, 6), ctx.getString(methodLong(res.method)), res.iterations)
    is Solved.Interp -> ctx.getString(R.string.result_text_interp, xEval, Fmt.fmt(res.pxe, 6))
}

fun methodLong(m: RootMethod) = when (m) {
    RootMethod.BISECTION -> R.string.method_bisection
    RootMethod.NEWTON -> R.string.method_newton_long
    RootMethod.SECANT -> R.string.method_secant
}

/** Clipboard and share-sheet actions. */
class Exporter(private val ctx: Context, private val copy: (String) -> Unit) {
    fun copyText(text: String) = copy(text)

    /** Writes the CSV to the cache and opens the share sheet through the FileProvider. */
    fun shareCsv(csv: String) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "numlab.csv").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun rememberExporter(): Exporter {
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(ctx) {
        Exporter(ctx) { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("NumLab", text))) } }
    }
}

/** Share button with Save / Copy result / CSV / LaTeX / Share CSV (prototype `ShareMenu`). */
@Composable
fun ShareMenu(solved: Solved?, onSave: () -> Unit, onCopied: (Int) -> Unit, container: Color = MaterialTheme.colorScheme.surface) {
    var open by remember { mutableStateOf(false) }
    val ex = rememberExporter()
    val ctx = LocalContext.current
    RoundBtn(Icons.Outlined.IosShare, stringResource(R.string.cd_share), { open = true }, container = container)
    DropdownMenu(open, { open = false }) {
        val ok = solved != null
        val close = { open = false }
        MenuRow(R.string.menu_save, Icons.Outlined.BookmarkBorder, ok) { close(); onSave() }
        MenuRow(R.string.menu_copy_result, Icons.Outlined.ContentCopy, ok) {
            close(); ex.copyText(solved!!.resultText(ctx)); onCopied(R.string.copied_result)
        }
        MenuRow(R.string.menu_copy_csv, Icons.Outlined.TableChart, ok) {
            close(); ex.copyText(solved!!.csv()); onCopied(R.string.copied_csv)
        }
        MenuRow(R.string.menu_copy_latex, Icons.AutoMirrored.Outlined.List, ok) {
            close(); ex.copyText(solved!!.latex()); onCopied(R.string.copied_latex)
        }
        MenuRow(R.string.menu_share_csv, Icons.Outlined.Share, ok) { close(); ex.shareCsv(solved!!.csv()) }
    }
}

@Composable
fun MenuRow(label: Int, icon: ImageVector, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) =
    DropdownMenuItem(
        { Text(stringResource(label), fontWeight = FontWeight.Medium,
            color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        onClick, leadingIcon = { Icon(icon, null) }, enabled = enabled,
    )
