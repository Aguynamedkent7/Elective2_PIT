package app.numlab.ui.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.numlab.ui.R
import app.numlab.ui.components.SimpleScreen

@Composable
fun CanvasScreen(module: String, onBack: () -> Unit) =
    SimpleScreen(module, stringResource(R.string.canvas_placeholder), onBack)
