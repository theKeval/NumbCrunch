package io.keval.apps.numbcrunch.gameplay.engagement

import androidx.compose.runtime.staticCompositionLocalOf
import io.keval.apps.numbcrunch.domain.engagement.AchievementSyncState
import kotlinx.coroutines.flow.StateFlow

enum class AchievementScreenStatus { IDLE, OPENING, UNAVAILABLE, CONFIGURATION_ERROR }

data class AchievementsBinding(
    val sync: StateFlow<AchievementSyncState>,
    val screen: StateFlow<AchievementScreenStatus>,
    val open: () -> Unit,
    val retry: () -> Unit,
    val surfaceActive: (Any, Boolean) -> Unit,
)

val LocalAchievements = staticCompositionLocalOf<AchievementsBinding?> { null }
