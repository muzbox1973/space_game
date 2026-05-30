package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scores")
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scoreValue: Float, // 생존한 시간 (초 단위)
    val timestamp: Long = System.currentTimeMillis()
)
