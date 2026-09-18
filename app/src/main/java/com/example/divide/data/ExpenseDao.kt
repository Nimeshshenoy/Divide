package com.example.divide.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for all financial and sync operations.
 */
@Dao
interface ExpenseDao {

    // --- READ OPERATIONS ---

    /**
     * Fetches all trips combined with their related users and expenses.
     * @Transaction is required because Room performs multiple queries under the hood.
     */
    @Transaction
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllTripsWithDetails(): Flow<List<TripWithDetails>>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getTripWithDetailsById(groupId: String): TripWithDetails?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun getProfile(): Flow<Profile?>

    // --- WRITE OPERATIONS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncEvent(event: SyncEvent)

    @Update
    suspend fun updateGroup(group: Group)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    // --- SYNC OPERATIONS ---

    /**
     * Used for Bluetooth syncing. Returns all events created after a specific time
     * or those not yet known to a peer.
     */
    @Query("SELECT * FROM sync_events WHERE createdAt > :sinceTimestamp ORDER BY createdAt ASC")
    suspend fun getSyncEventsSince(sinceTimestamp: Long): List<SyncEvent>
}
