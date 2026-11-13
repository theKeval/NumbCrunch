package io.keval.apps.numbcrunch.domain.connection

import io.keval.apps.numbcrunch.domain.progress.PlayerProfile
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ConnectionStatus { CHECKING, CONNECTED, NOT_CONNECTED, UNAVAILABLE, CANCELLED, ERROR }

data class PlayGamesPlayer(val id: String, val displayName: String)

sealed interface ConnectionResult {
    data class Connected(val player: PlayGamesPlayer) : ConnectionResult
    data object NotConnected : ConnectionResult
    data object Unavailable : ConnectionResult
    data object Cancelled : ConnectionResult
    data object Failed : ConnectionResult
}

/** Interactive authentication is called only after the player requests it. */
fun interface PlayGamesGateway {
    suspend fun authenticate(interactive: Boolean): ConnectionResult
}

data class PlayerSession(
    val status: ConnectionStatus = ConnectionStatus.NOT_CONNECTED,
    val selectedProfile: PlayerProfile? = null,
    val player: PlayGamesPlayer? = null,
    val accountProfile: PlayerProfile? = null,
    val unclaimedGuest: PlayerProfile? = null,
    val importing: Boolean = false,
    val localError: Boolean = false,
    val generation: Long = 0,
) {
    val busy: Boolean get() = status == ConnectionStatus.CHECKING || importing
    /** Projections must capture this generation and recheck it before using an asynchronous result. */
    val connectedAccountId: String? get() = accountProfile?.id.takeIf { status == ConnectionStatus.CONNECTED }
}

/** Holds no Activity, credentials, email or cloud-sync claims. All local progress survives auth failures. */
class PlayerConnectionController(private val progress: ProgressRepository) {
    private val mutex = Mutex()
    private val mutableSession = MutableStateFlow(PlayerSession())
    val session: StateFlow<PlayerSession> = mutableSession.asStateFlow()
    private var generation = 0L

    suspend fun initialize() = mutex.withLock {
        try {
            updateLocalState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableSession.value = mutableSession.value.copy(localError = true)
        }
    }

    suspend fun refresh(gateway: PlayGamesGateway, interactive: Boolean = false) {
        val request = mutex.withLock {
            generation += 1
            mutableSession.value = mutableSession.value.copy(
                status = ConnectionStatus.CHECKING, player = null, accountProfile = null,
                generation = generation, localError = false,
            )
            generation
        }
        val result = try {
            gateway.authenticate(interactive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ConnectionResult.Failed
        }
        mutex.withLock {
            if (request != generation) return
            try {
                if (result is ConnectionResult.Connected) {
                    require(result.player.id.isNotBlank()) { "Missing authenticated player" }
                    val account = progress.activatePlayerProfile(result.player.id)
                    mutableSession.value = mutableSession.value.copy(
                        status = ConnectionStatus.CONNECTED, player = result.player, accountProfile = account,
                    )
                } else {
                    mutableSession.value = mutableSession.value.copy(status = when (result) {
                        ConnectionResult.NotConnected -> ConnectionStatus.NOT_CONNECTED
                        ConnectionResult.Unavailable -> ConnectionStatus.UNAVAILABLE
                        ConnectionResult.Cancelled -> ConnectionStatus.CANCELLED
                        else -> ConnectionStatus.ERROR
                    })
                }
                updateLocalState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableSession.value = mutableSession.value.copy(
                    status = ConnectionStatus.ERROR, player = null, accountProfile = null, localError = true,
                )
            }
        }
    }

    /** Stop delivery before an Activity/account change; late responses cannot reactivate the old account. */
    suspend fun suspendConnection() = mutex.withLock {
        generation += 1
        mutableSession.value = mutableSession.value.copy(
            status = ConnectionStatus.NOT_CONNECTED, player = null, accountProfile = null, generation = generation,
        )
    }

    suspend fun selectGuest() = localChange {
        progress.selectProfile(progress.guestProfile().id)
    }

    suspend fun selectConnectedProfile() = localChange {
        val account = mutableSession.value.accountProfile ?: return@localChange
        progress.selectProfile(account.id)
    }

    suspend fun importGuest(expectedAccountProfileId: String) = mutex.withLock {
        val before = mutableSession.value
        if (before.busy || before.status != ConnectionStatus.CONNECTED) return
        val guest = before.unclaimedGuest ?: return
        val account = before.accountProfile ?: return
        if (account.id != expectedAccountProfileId) return
        generation += 1
        mutableSession.value = before.copy(importing = true, localError = false, generation = generation)
        try {
            progress.claimGuestProgress(guest.id, account.id)
            progress.selectProfile(account.id)
            updateLocalState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableSession.value = mutableSession.value.copy(localError = true)
        } finally {
            mutableSession.value = mutableSession.value.copy(importing = false)
        }
    }

    private suspend fun localChange(action: suspend () -> Unit) = mutex.withLock {
        if (mutableSession.value.busy) return
        try {
            generation += 1
            mutableSession.value = mutableSession.value.copy(generation = generation)
            action()
            updateLocalState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableSession.value = mutableSession.value.copy(localError = true)
        }
    }

    private suspend fun updateLocalState() {
        val selected = progress.selectedProfile()
        val guest = progress.guestProfile()
        val guestProgress = progress.exportProgress(guest.id)
        val hasGuestProgress = guestProgress.rounds.isNotEmpty() || guestProgress.outcomes.isNotEmpty() ||
            guestProgress.tutorialCompletions.isNotEmpty()
        mutableSession.value = mutableSession.value.copy(
            selectedProfile = selected, unclaimedGuest = guest.takeIf { hasGuestProgress }, localError = false,
        )
    }
}
