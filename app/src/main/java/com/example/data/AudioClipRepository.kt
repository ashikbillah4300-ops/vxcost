package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AudioClipRepository(
    private val context: Context,
    private val dao: AudioClipDao
) {
    val allClips: Flow<List<AudioClipEntity>> = dao.getAllClips()

    private val clipsDir: File
        get() = File(context.filesDir, "audio_clips").apply {
            if (!exists()) mkdirs()
        }

    suspend fun importAudioFromUri(uri: Uri): Result<AudioClipEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val displayName = resolveDisplayName(uri) ?: "Audio_${System.currentTimeMillis()}"
            val cleanTitle = displayName.substringBeforeLast('.').ifBlank { displayName }
            val ext = displayName.substringAfterLast('.', "mp3").lowercase().take(6)
            val destFile = File(clipsDir, "${UUID.randomUUID()}.$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open selected audio file")

            if (destFile.length() == 0L) {
                destFile.delete()
                error("Selected audio file is empty")
            }

            val durationMs = extractDurationMs(destFile.absolutePath)
            val mimeType = contentResolver.getType(uri) ?: "audio/*"

            val entity = AudioClipEntity(
                title = cleanTitle,
                filePath = destFile.absolutePath,
                durationMs = durationMs,
                fileSizeBytes = destFile.length(),
                mimeType = mimeType,
                category = "Uploaded"
            )
            val id = dao.insertClip(entity)
            entity.copy(id = id)
        }
    }

    suspend fun registerRecordedClip(
        file: File,
        title: String,
        durationMs: Long
    ): AudioClipEntity = withContext(Dispatchers.IO) {
        val entity = AudioClipEntity(
            title = title,
            filePath = file.absolutePath,
            durationMs = durationMs,
            fileSizeBytes = file.length(),
            mimeType = "audio/wav",
            category = "Recorded"
        )
        val id = dao.insertClip(entity)
        entity.copy(id = id)
    }

    fun createRecordingFile(): File {
        return File(clipsDir, "rec_${System.currentTimeMillis()}.wav")
    }

    suspend fun renameClip(clipId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameClip(clipId, trimmed)
        }
    }

    suspend fun deleteClip(clip: AudioClipEntity) = withContext(Dispatchers.IO) {
        dao.deleteClipById(clip.id)
        runCatching {
            val f = File(clip.filePath)
            if (f.exists()) f.delete()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }

    private fun extractDurationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }
}
