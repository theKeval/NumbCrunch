package io.keval.apps.numbcrunch.gameplay.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.common.GuideAction
import io.keval.apps.numbcrunch.gameplay.common.GuideBody
import io.keval.apps.numbcrunch.gameplay.common.GuidePanel
import io.keval.apps.numbcrunch.gameplay.common.GuideSkipAction
import io.keval.apps.numbcrunch.gameplay.common.NumbCrunchScreenScaffold

@Composable
fun WelcomeScreen(
    onViewTutorial: () -> Unit,
    onGoHome: () -> Unit,
    isCompleting: Boolean,
    completionError: Boolean,
    modifier: Modifier = Modifier,
) {
    NumbCrunchScreenScaffold(modifier) {
        Column(
            modifier = Modifier.align(Alignment.Center)
                .widthIn(max = 420.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.home_title),
                contentDescription = stringResource(R.string.cd_game_title),
                modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            )
            GuidePanel(stringResource(R.string.welcome_title)) {
                GuideBody(stringResource(R.string.welcome_goal))
                GuideBody(stringResource(R.string.welcome_clues))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GuideAction(
                        text = stringResource(R.string.welcome_view_tutorial),
                        onClick = onViewTutorial,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCompleting,
                    )
                    GuideSkipAction(
                        text = stringResource(R.string.welcome_go_home),
                        onClick = onGoHome,
                        enabled = !isCompleting,
                    )
                }

                val status = when {
                    isCompleting -> R.string.welcome_saving
                    completionError -> R.string.tutorial_completion_error
                    else -> null
                }
                if (status != null) {
                    GuideBody(
                        stringResource(status),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Preview(name = "Compact phone", widthDp = 320, heightDp = 568)
@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable
private fun WelcomePreview() {
    NumbCrunchTheme { WelcomeScreen({}, {}, isCompleting = false, completionError = false) }
}
