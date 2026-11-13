package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioClipDao {
    @Query("SELECT * FROM audio_clips ORDER BY createdAt DESC")
    fun getAllClips(): Flow<List<AudioClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: AudioClipEntity): Long

    @Query("UPDATE audio_clips SET title = :newTitle WHERE id = :clipId")
    suspend fun renameClip(clipId: Long, newTitle: String)

    @Query("DELETE FROM audio_clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: Long)

    @Query("SELECT * FROM audio_clips WHERE id = :clipId LIMIT 1")
    suspend fun getClipById(clipId: Long): AudioClipEntity?
}
