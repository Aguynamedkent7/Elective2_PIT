package app.numlab.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.numlab.ui.R
import app.numlab.ui.theme.Bricolage

/** Header lockup: 32 dp tile + "NumLab" (Num ExtraBold, Lab Medium at 80 %). */
@Composable
fun LogoLockup(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_logo), stringResource(R.string.cd_logo), Modifier.size(32.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = ink)) {
                    append(stringResource(R.string.wordmark_num))
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.8f))) {
                    append(stringResource(R.string.wordmark_lab))
                }
            },
            fontFamily = Bricolage,
            // Brand lockup: kept at its designed size so the header never wraps at 200 % font scale.
            fontSize = (24 / LocalDensity.current.fontScale).sp,
            maxLines = 1, softWrap = false,
        )
    }
}
