package org.ralphthon.haruman_ralphthon.data

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

/** Large, recoverable UI payloads stay in private files, never in an activity Bundle. */
internal class UiWorkspaceStore(private val directory: File) {
    fun read(token: String): String? = runCatching {
        file(token).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    /** AtomicFile keeps the previous complete payload if writing or syncing fails. */
    fun write(token: String, value: String): Boolean {
        var stream: FileOutputStream? = null
        val target = file(token)
        return try {
            if (!directory.isDirectory && !directory.mkdirs()) return false
            stream = target.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
            true
        } catch (_: Exception) {
            runCatching { target.failWrite(stream) }
            false
        }
    }

    private fun file(token: String): AtomicFile {
        require(token.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        return AtomicFile(File(directory, "$token.json"))
    }
}
