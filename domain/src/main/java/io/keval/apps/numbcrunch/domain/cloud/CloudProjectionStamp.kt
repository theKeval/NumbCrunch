package io.keval.apps.numbcrunch.domain.cloud

import io.keval.apps.numbcrunch.domain.progress.ProgressSnapshot
import java.security.MessageDigest

/** Exact canonical local evidence coverage. Never log/display this stamp or use it as a player identity. */
fun ProgressSnapshot.cloudProjectionStamp(): String {
    return CloudProgress.fromSnapshot(this).cloudProjectionStamp()
}

fun CloudProgress.cloudProjectionStamp(): String {
    val evidence = copy(versions = versions.map { it.unfinished?.let(CloudRoundVersion::initial)
        ?: CloudRoundVersion.initial(requireNotNull(it.outcome)) })
    val manifest = CloudCodec.encode(evidence).manifest
    return MessageDigest.getInstance("SHA-256").digest(manifest).joinToString("") { "%02x".format(it) }
}
