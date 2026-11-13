package io.keval.apps.numbcrunch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.connection.ConnectionStatus
import io.keval.apps.numbcrunch.domain.connection.PlayGamesPlayer
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import io.keval.apps.numbcrunch.domain.progress.PlayerProfile
import io.keval.apps.numbcrunch.gameplay.connection.PlayGamesConnectionCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayGamesConnectionCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disconnectedActionWorksAndCheckingDisablesRepeatedRequests() {
        val session = mutableStateOf(PlayerSession(status = ConnectionStatus.CANCELLED))
        var requests = 0
        compose.setContent {
            NumbCrunchTheme {
                PlayGamesConnectionCard(session.value, { requests++ }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Connection cancelled. You can keep playing.").assertIsDisplayed()
        compose.onNodeWithText("Connect Play Games").performClick()
        compose.runOnIdle { assertEquals(1, requests); session.value = PlayerSession(status = ConnectionStatus.CHECKING) }
        compose.onNodeWithText("Connect Play Games").assertIsNotEnabled()
        compose.onNodeWithText("Progress is saved on this device. Connecting does not turn on cloud backup.").assertIsDisplayed()
    }

    @Test fun accountChangeDismissesOldGuestImportConsent() {
        val guest = PlayerProfile("guest", null, 1)
        val a = PlayerProfile("a", "pgs-a", 1)
        val b = PlayerProfile("b", "pgs-b", 1)
        val session = mutableStateOf(PlayerSession(status = ConnectionStatus.CONNECTED, selectedProfile = a,
            accountProfile = a, player = PlayGamesPlayer("pgs-a", "Player A"), unclaimedGuest = guest))
        var claims = 0
        compose.setContent {
            NumbCrunchTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PlayGamesConnectionCard(session.value, {}, {}, {}, {}, { claims++ })
                }
            }
        }
        compose.onNodeWithText("Connect Play Games").assertDoesNotExist()
        compose.onNodeWithText("Import device-only progress").performScrollTo().performClick()
        compose.onNodeWithText("Import progress").assertIsDisplayed()
        compose.runOnIdle {
            session.value = session.value.copy(accountProfile = b, selectedProfile = b, player = PlayGamesPlayer("pgs-b", "Player B"))
        }
        compose.onNodeWithText("Import progress").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, claims) }
    }
}
