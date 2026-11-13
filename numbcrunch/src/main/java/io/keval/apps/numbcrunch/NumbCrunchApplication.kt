package io.keval.apps.numbcrunch

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class NumbCrunchApplication : Application() {
    val appContainer: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        PlayGamesSdk.initialize(this)
    }
}
