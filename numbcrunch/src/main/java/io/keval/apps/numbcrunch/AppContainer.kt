package io.keval.apps.numbcrunch

import android.content.Context
import io.keval.apps.numbcrunch.data.di.RepositoryModule
import io.keval.apps.numbcrunch.domain.repository.TutorialRepository
import io.keval.apps.numbcrunch.domain.usecase.CompleteFirstRunTutorialUseCase
import io.keval.apps.numbcrunch.domain.usecase.GetFriendSecretUseCase
import io.keval.apps.numbcrunch.domain.usecase.GetWelcomeMessageUseCase
import io.keval.apps.numbcrunch.domain.usecase.ObserveAudioSettingsUseCase
import io.keval.apps.numbcrunch.domain.usecase.ObserveFirstRunTutorialUseCase
import io.keval.apps.numbcrunch.domain.usecase.SaveFriendSecretUseCase
import io.keval.apps.numbcrunch.domain.usecase.SetMusicEnabledUseCase
import io.keval.apps.numbcrunch.domain.usecase.SetSoundEffectsEnabledUseCase
import io.keval.apps.numbcrunch.gameplay.audio.NumbCrunchAudio

class AppContainer(
    context: Context,
    tutorialRepository: TutorialRepository = RepositoryModule.createTutorialRepository(context),
) {
    val progressRepository = RepositoryModule.createProgressRepository(context)
    private val appInfoRepository = RepositoryModule.createAppInfoRepository()
    private val gameSessionRepository = RepositoryModule.createGameSessionRepository()
    private val settingsRepository = RepositoryModule.createSettingsRepository(context)

    val getWelcomeMessageUseCase: GetWelcomeMessageUseCase =
        GetWelcomeMessageUseCase(appInfoRepository)

    val saveFriendSecretUseCase: SaveFriendSecretUseCase =
        SaveFriendSecretUseCase(gameSessionRepository)

    val getFriendSecretUseCase: GetFriendSecretUseCase =
        GetFriendSecretUseCase(gameSessionRepository)

    val observeFirstRunTutorialUseCase: ObserveFirstRunTutorialUseCase =
        ObserveFirstRunTutorialUseCase(tutorialRepository)

    val completeFirstRunTutorialUseCase: CompleteFirstRunTutorialUseCase =
        CompleteFirstRunTutorialUseCase(tutorialRepository)

    val observeAudioSettingsUseCase: ObserveAudioSettingsUseCase =
        ObserveAudioSettingsUseCase(settingsRepository)

    val setMusicEnabledUseCase: SetMusicEnabledUseCase =
        SetMusicEnabledUseCase(settingsRepository)

    val setSoundEffectsEnabledUseCase: SetSoundEffectsEnabledUseCase =
        SetSoundEffectsEnabledUseCase(settingsRepository)

    val audio: NumbCrunchAudio by lazy { NumbCrunchAudio(context) }
}
