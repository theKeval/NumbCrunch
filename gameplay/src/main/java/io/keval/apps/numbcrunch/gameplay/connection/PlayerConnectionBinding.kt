package io.keval.apps.numbcrunch.gameplay.connection

import androidx.compose.runtime.staticCompositionLocalOf
import io.keval.apps.numbcrunch.domain.connection.PlayerSession
import kotlinx.coroutines.flow.StateFlow

data class PlayerConnectionBinding(
    val session: StateFlow<PlayerSession>,
    val connect: () -> Unit,
    val refresh: () -> Unit,
    val refreshLocal: () -> Unit,
    val selectGuest: () -> Unit,
    val selectAccount: () -> Unit,
    val importGuest: (String) -> Unit,
)

val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnectionBinding?> { null }
