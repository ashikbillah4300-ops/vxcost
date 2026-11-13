package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_clips")
data class AudioClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val mimeType: String,
    val category: String, // "Uploaded" or "Recorded"
    val createdAt: Long = System.currentTimeMillis()
)
