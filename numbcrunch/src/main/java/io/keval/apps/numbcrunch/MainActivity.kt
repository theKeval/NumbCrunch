package io.keval.apps.numbcrunch

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.PlayGames
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchTheme
import io.keval.apps.numbcrunch.gameplay.audio.LocalGameSoundPlayer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appContainer: AppContainer
        get() = (application as NumbCrunchApplication).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            PlayGames.getGamesSignInClient(this).isAuthenticated().addOnCompleteListener { task ->
                val authenticated = task.isSuccessful && task.result.isAuthenticated
                val statusCode = (task.exception as? ApiException)?.statusCode
                Log.d("NumbCrunchPlayGames", "Authenticated: $authenticated (status: $statusCode)")
            }
        }
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
            CompositionLocalProvider(LocalGameSoundPlayer provides audio) {
                NumbCrunchTheme {
                    NumbCrunchApp(appContainer = appContainer)
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) appContainer.audio.releaseMusic()
        super.onDestroy()
    }
}
