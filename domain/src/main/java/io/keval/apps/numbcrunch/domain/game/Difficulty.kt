package io.keval.apps.numbcrunch.domain.game

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD;

    companion object {
        fun fromName(name: String?): Difficulty? = entries.firstOrNull { it.name == name }
    }
}
