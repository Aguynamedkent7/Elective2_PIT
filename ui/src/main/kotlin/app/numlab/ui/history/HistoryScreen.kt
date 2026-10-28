package app.numlab.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.numlab.ui.R
import app.numlab.ui.components.SimpleScreen

@Composable
fun HistoryScreen(onBack: () -> Unit) =
    SimpleScreen(stringResource(R.string.history_title), stringResource(R.string.history_empty), onBack)
