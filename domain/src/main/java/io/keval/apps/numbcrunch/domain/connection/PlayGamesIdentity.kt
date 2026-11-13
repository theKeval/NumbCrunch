package io.keval.apps.numbcrunch.domain.connection

/** Compare repeated reads from one identity API; different API representations need not compare equal. */
suspend fun readStablePlayGamesPlayer(load: suspend () -> PlayGamesPlayer): ConnectionResult {
    val before = load()
    val after = load()
    return if (before.id.isNotBlank() && before.id == after.id) ConnectionResult.Connected(after)
    else ConnectionResult.Failed
}
