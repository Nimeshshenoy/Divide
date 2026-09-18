package com.example.divide.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM trips")
    fun getAllTrips(): Flow<List<Trip>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip)

    @Update
    suspend fun updateTrip(trip: Trip)

    @Delete
    suspend fun deleteTrip(trip: Trip)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): Trip?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun getProfile(): Flow<Profile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)
}
