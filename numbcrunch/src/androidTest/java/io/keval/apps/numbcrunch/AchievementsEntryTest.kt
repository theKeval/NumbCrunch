package io.keval.apps.numbcrunch

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.domain.connection.*
import io.keval.apps.numbcrunch.domain.engagement.AchievementSyncState
import io.keval.apps.numbcrunch.domain.progress.PlayerProfile
import io.keval.apps.numbcrunch.gameplay.R
import io.keval.apps.numbcrunch.gameplay.connection.LocalPlayerConnection
import io.keval.apps.numbcrunch.gameplay.connection.PlayerConnectionBinding
import io.keval.apps.numbcrunch.gameplay.engagement.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AchievementsEntryTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun disconnectedEntryExplainsOfflinePlayThenOffersNativePanelAfterConnection() {
        val session = MutableStateFlow(PlayerSession())
        var connects = 0
        var opens = 0
        var selections = 0
        val activeSurfaces = mutableSetOf<Any>()
        val connection = PlayerConnectionBinding(session, { connects++ }, {}, {}, {}, { selections++ }, {})
        val achievements = AchievementsBinding(MutableStateFlow(AchievementSyncState()),
            MutableStateFlow(AchievementScreenStatus.IDLE), { opens++ }, {},
            { token, active -> if (active) activeSurfaces.add(token) else activeSurfaces.remove(token) })
        compose.setContent {
            CompositionLocalProvider(LocalPlayerConnection provides connection, LocalAchievements provides achievements) {
                NumbCrunchTheme { AchievementsRoute() }
            }
        }
        compose.onNodeWithText(context.getString(R.string.achievements_connect_explanation)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.achievements_connect)).performClick()
        compose.runOnIdle {
            assertEquals(1, connects)
            assertEquals(0, opens)
            assertEquals(1, activeSurfaces.size)
            session.value = PlayerSession(status = ConnectionStatus.CONNECTED,
                player = PlayGamesPlayer("player", "Player"), accountProfile = PlayerProfile("local", "player", 1),
                selectedProfile = PlayerProfile("guest", null, 1))
        }
        compose.onNodeWithText(context.getString(R.string.play_games_view_account)).performClick()
        compose.runOnIdle {
            assertEquals(1, selections)
            assertEquals(0, opens)
            session.value = session.value.copy(selectedProfile = session.value.accountProfile)
        }
        compose.onNodeWithText(context.getString(R.string.achievements_view)).performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }
}
