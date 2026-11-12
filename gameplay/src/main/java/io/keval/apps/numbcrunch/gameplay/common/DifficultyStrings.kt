package io.keval.apps.numbcrunch.gameplay.common

import androidx.annotation.StringRes
import io.keval.apps.numbcrunch.domain.game.Difficulty
import io.keval.apps.numbcrunch.gameplay.R

@get:StringRes
internal val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
    }
