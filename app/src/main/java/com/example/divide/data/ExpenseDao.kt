package com.example.divide.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for all financial and sync operations.
 */
@Dao
interface ExpenseDao {

    // --- READ OPERATIONS ---

    @Transaction
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllTripsWithDetails(): Flow<List<TripWithDetails>>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getTripWithDetailsById(groupId: String): TripWithDetails?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun getProfile(): Flow<Profile?>

    // --- BASE WRITE OPERATIONS ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    // Task 3: IGNORE duplicate events to maintain an immutable log
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncEvent(event: SyncEvent)

    @Update
    suspend fun updateGroup(group: Group)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteGroup(group: Group)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    // --- ATOMIC TRANSACTIONAL OPERATIONS (Task 1) ---

    /**
     * Atomically creates a trip and its initial sync event.
     */
    @Transaction
    suspend fun insertTripWithSync(group: Group, users: List<User>, syncEvent: SyncEvent) {
        insertGroup(group)
        users.forEach { insertUser(it) }
        insertSyncEvent(syncEvent)
    }

    /**
     * Atomically adds an expense and logs the sync event.
     */
    @Transaction
    suspend fun insertExpenseWithSync(expense: Expense, syncEvent: SyncEvent) {
        insertExpense(expense)
        insertSyncEvent(syncEvent)
    }

    /**
     * Atomically updates an expense and logs the sync event.
     */
    @Transaction
    suspend fun updateExpenseWithSync(expense: Expense, syncEvent: SyncEvent) {
        updateExpense(expense)
        insertSyncEvent(syncEvent)
    }
}
