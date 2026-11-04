package com.example.data.local

import kotlinx.coroutines.flow.Flow

interface SavedMediaDao {
    fun getAllByCustomOrder(): Flow<List<SavedMediaEntity>>
    fun getAllByDateDesc(): Flow<List<SavedMediaEntity>>
    fun getAllByDateAsc(): Flow<List<SavedMediaEntity>>
    fun getAllByTitleAsc(): Flow<List<SavedMediaEntity>>
    fun getAllByRatingDesc(): Flow<List<SavedMediaEntity>>
    fun getAllByRatingAsc(): Flow<List<SavedMediaEntity>>
    fun isSavedFlow(id: String): Flow<Boolean>
    suspend fun exists(id: String): Boolean
    suspend fun getCount(): Int
    suspend fun insert(item: SavedMediaEntity)
    suspend fun updateAll(items: List<SavedMediaEntity>)
    suspend fun deleteById(id: String)
}
