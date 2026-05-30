package com.example.data

import kotlinx.coroutines.flow.Flow

class ScoreRepository(private val scoreDao: ScoreDao) {
    val highScore: Flow<ScoreEntity?> = scoreDao.getHighScore()
    val allScores: Flow<List<ScoreEntity>> = scoreDao.getAllScores()

    suspend fun insertScore(scoreValue: Float) {
        scoreDao.insertScore(ScoreEntity(scoreValue = scoreValue))
    }

    suspend fun clearScores() {
        scoreDao.clearAllScores()
    }
}
