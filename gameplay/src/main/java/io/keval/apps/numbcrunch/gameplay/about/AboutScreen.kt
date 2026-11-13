package io.keval.apps.numbcrunch.gameplay.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import io.keval.apps.numbcrunch.core.ui.artwork.rememberArtworkPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.GuideAction
import io.keval.apps.numbcrunch.gameplay.common.GuideBody
import io.keval.apps.numbcrunch.gameplay.common.GuidePanel
import io.keval.apps.numbcrunch.gameplay.common.GuideScaffold

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val version = remember(context, preview) {
        if (preview) "1.0" else {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    }
    GuideScaffold(stringResource(R.string.label_about), onBack, modifier) {
        Image(
            rememberArtworkPainter(R.drawable.home_title), stringResource(R.string.cd_game_title),
            Modifier.fillMaxWidth().height(120.dp),
        )
        GuidePanel(stringResource(R.string.about_story_title)) {
            GuideBody(stringResource(R.string.about_story_body))
        }
        GuidePanel(stringResource(R.string.about_tagline)) {
            GuideBody(stringResource(R.string.about_intro))
        }
        GuidePanel(stringResource(R.string.about_mind_title)) {
            GuideBody(stringResource(R.string.about_mind_body))
        }
        GuidePanel(stringResource(R.string.about_credits_title)) {
            GuideBody(stringResource(R.string.about_credits_body))
            GuideBody(stringResource(R.string.about_version, version ?: stringResource(R.string.about_version_unknown)))
        }
        GuidePanel(stringResource(R.string.about_privacy_title)) {
            GuideBody(stringResource(R.string.about_privacy_body))
            GuideAction(stringResource(R.string.about_privacy_action), onPrivacyPolicyClick, Modifier.fillMaxWidth())
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(name = "Large text", widthDp = 320, heightDp = 568, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 800, heightDp = 1280)
@Composable
private fun AboutPreview() {
    NumbCrunchTheme { AboutScreen(onBack = {}, onPrivacyPolicyClick = {}) }
}
