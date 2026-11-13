package io.keval.apps.numbcrunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.gameplay.audio.LocalGameSoundPlayer
import io.keval.apps.numbcrunch.gameplay.connection.LocalPlayerConnection
import io.keval.apps.numbcrunch.gameplay.connection.PlayerConnectionBinding
import io.keval.apps.numbcrunch.playgames.AndroidPlayGamesGateway
import io.keval.apps.numbcrunch.playgames.PlayerConnectionViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as NumbCrunchApplication).appContainer

    private val playerConnection by lazy {
        ViewModelProvider(this, PlayerConnectionViewModel.factory(appContainer.progressRepository))[PlayerConnectionViewModel::class.java]
    }
    private var connectionJob: Job? = null
    private var interactiveConnection = false
    private var connectionRequest = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val audio = appContainer.audio
        // Background music follows the saved settings only while the app is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audio.setInForeground(true)
                try {
                    appContainer.observeAudioSettingsUseCase().collect(audio::applySettings)
                } finally {
                    audio.setInForeground(false)
                }
            }
        }
        setContent {
            CompositionLocalProvider(
                LocalGameSoundPlayer provides audio,
                LocalPlayerConnection provides PlayerConnectionBinding(
                    session = playerConnection.connection.session,
                    connect = { requestConnection(interactive = true) },
                    refresh = { requestConnection(interactive = false) },
                    refreshLocal = { lifecycleScope.launch { playerConnection.connection.initialize() } },
                    selectGuest = { lifecycleScope.launch { playerConnection.connection.selectGuest() } },
                    selectAccount = { lifecycleScope.launch { playerConnection.connection.selectConnectedProfile() } },
                    importGuest = { accountId -> lifecycleScope.launch { playerConnection.connection.importGuest(accountId) } },
                ),
            ) {
                NumbCrunchTheme {
                    NumbCrunchApp(appContainer = appContainer)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!interactiveConnection) requestConnection(interactive = false)
    }

    private fun requestConnection(interactive: Boolean) {
        if (interactiveConnection) return
        connectionJob?.cancel()
        val request = ++connectionRequest
        interactiveConnection = interactive
        connectionJob = lifecycleScope.launch {
            try {
                playerConnection.connection.refresh(AndroidPlayGamesGateway(this@MainActivity), interactive)
            } finally {
                if (request == connectionRequest) interactiveConnection = false
            }
        }
    }

    override fun onStop() {
        if (!interactiveConnection) {
            connectionJob?.cancel()
            playerConnection.suspendConnection()
        }
        super.onStop()
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        playerConnection.suspendConnection()
        if (isFinishing) appContainer.audio.releaseMusic()
        super.onDestroy()
    }
}
