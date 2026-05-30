package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("SELECT * FROM scores ORDER BY scoreValue DESC LIMIT 1")
    fun getHighScore(): Flow<ScoreEntity?>

    @Query("SELECT * FROM scores ORDER BY timestamp DESC")
    fun getAllScores(): Flow<List<ScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: ScoreEntity)

    @Query("DELETE FROM scores")
    suspend fun clearAllScores()
}
