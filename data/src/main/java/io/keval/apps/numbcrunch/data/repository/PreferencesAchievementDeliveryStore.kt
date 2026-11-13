package io.keval.apps.numbcrunch.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import io.keval.apps.numbcrunch.domain.engagement.AchievementCatalog
import io.keval.apps.numbcrunch.domain.engagement.AchievementDeliveryStore
import io.keval.apps.numbcrunch.domain.engagement.AchievementKey
import kotlinx.coroutines.flow.first

/** Acknowledged absolute progress, never an increment/event ledger. Safe to restore for the same PGS player. */
class PreferencesAchievementDeliveryStore(context: Context) : AchievementDeliveryStore {
    private val preferences = context.applicationContext.numbCrunchPreferences

    override suspend fun acknowledgedSteps(playerId: String): Map<AchievementKey, Int> {
        require(playerId.isNotBlank())
        val saved = preferences.data.first()
        return AchievementCatalog.definitions.associate { definition ->
            definition.key to (saved[preferenceKey(playerId, definition.key)] ?: 0).coerceIn(0, definition.target)
        }
    }

    override suspend fun acknowledge(playerId: String, key: AchievementKey, steps: Int) {
        require(playerId.isNotBlank())
        val target = AchievementCatalog.definitions.single { it.key == key }.target
        require(steps in 1..target)
        val storageKey = preferenceKey(playerId, key)
        preferences.edit { it[storageKey] = maxOf(it[storageKey] ?: 0, steps) }
    }

    private fun preferenceKey(playerId: String, achievement: AchievementKey) =
        intPreferencesKey("achievement_ack_v${AchievementCatalog.VERSION}_${playerId}_${achievement.logicalKey}")
}
