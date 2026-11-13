package io.keval.apps.numbcrunch.data.di

import android.content.Context
import io.keval.apps.numbcrunch.data.progress.SqliteProgressRepository
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import io.keval.apps.numbcrunch.data.repository.DefaultAppInfoRepository
import io.keval.apps.numbcrunch.data.repository.InMemoryGameSessionRepository
import io.keval.apps.numbcrunch.data.repository.PreferencesSettingsRepository
import io.keval.apps.numbcrunch.data.repository.PreferencesTutorialRepository
import io.keval.apps.numbcrunch.domain.repository.AppInfoRepository
import io.keval.apps.numbcrunch.domain.repository.GameSessionRepository
import io.keval.apps.numbcrunch.domain.repository.SettingsRepository
import io.keval.apps.numbcrunch.domain.repository.TutorialRepository

object RepositoryModule {
    fun createProgressRepository(context: Context): ProgressRepository = SqliteProgressRepository(context)

    fun createAppInfoRepository(): AppInfoRepository {
        return DefaultAppInfoRepository()
    }

    fun createGameSessionRepository(): GameSessionRepository {
        return InMemoryGameSessionRepository()
    }

    fun createTutorialRepository(context: Context): TutorialRepository {
        return PreferencesTutorialRepository(context)
    }

    fun createSettingsRepository(context: Context): SettingsRepository {
        return PreferencesSettingsRepository(context)
    }
}
