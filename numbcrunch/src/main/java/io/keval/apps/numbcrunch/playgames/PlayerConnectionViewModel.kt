package io.keval.apps.numbcrunch.playgames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.keval.apps.numbcrunch.domain.connection.PlayerConnectionController
import io.keval.apps.numbcrunch.domain.progress.ProgressRepository
import kotlinx.coroutines.launch

class PlayerConnectionViewModel(progress: ProgressRepository) : ViewModel() {
    val connection = PlayerConnectionController(progress)

    fun suspendConnection() {
        viewModelScope.launch { connection.suspendConnection() }
    }

    companion object {
        fun factory(progress: ProgressRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(PlayerConnectionViewModel::class.java))
                return PlayerConnectionViewModel(progress) as T
            }
        }
    }
}
