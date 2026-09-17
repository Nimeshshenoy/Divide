package com.example.divide.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM events")
    fun getAllEvents(): Flow<List<ExpenseEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ExpenseEvent)

    @Update
    suspend fun updateEvent(event: ExpenseEvent)

    @Delete
    suspend fun deleteEvent(event: ExpenseEvent)

    @Query("SELECT * FROM events WHERE id = :eventId")
    suspend fun getEventById(eventId: String): ExpenseEvent?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun getProfile(): Flow<Profile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)
}
