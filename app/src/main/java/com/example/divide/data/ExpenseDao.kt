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
    @Query("SELECT * FROM `groups` WHERE id = :groupId")
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

    // --- LOOKUP OPERATIONS (For Sync/Conflict Resolution) ---

    @Query("SELECT * FROM sync_events WHERE eventId = :eventId")
    suspend fun getSyncEventById(eventId: String): SyncEvent?

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): Expense?

    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): Group?

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("""
        SELECT EXISTS(
            SELECT 1
            FROM users
            WHERE id = :userId
            AND groupId = :groupId
        )
    """)
    suspend fun isUserMemberOfGroup(
        userId: String,
        groupId: String
    ): Boolean

    @Query("SELECT eventId FROM sync_events WHERE groupId = :groupId")
    suspend fun getSyncEventIdsForGroup(groupId: String): List<String>

    @Query("SELECT * FROM sync_events WHERE groupId = :groupId ORDER BY createdAt ASC")
    fun getSyncEventsFlowForGroup(groupId: String): Flow<List<SyncEvent>>

    @Query("SELECT * FROM sync_events WHERE groupId = :groupId AND type = 'ADD_TRIP' LIMIT 1")
    suspend fun getAddTripSyncEventForGroup(groupId: String): SyncEvent?

    @Query("SELECT * FROM sync_events WHERE groupId = :groupId AND eventId IN (:eventIds)")
    suspend fun getSyncEventsByIds(groupId: String, eventIds: List<String>): List<SyncEvent>

    @Query("SELECT * FROM users WHERE groupId = :groupId AND isLocalPlayer = 1 LIMIT 1")
    suspend fun getLocalUserForGroup(groupId: String): User?

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users WHERE isLocalPlayer = 1 AND id != :profileUserId")
    suspend fun getLegacyLocalUsers(profileUserId: String): List<User>

    @Query("SELECT * FROM users WHERE id = :userId AND groupId = :groupId LIMIT 1")
    suspend fun getUserByIdAndGroup(userId: String, groupId: String): User?

    @Query("UPDATE users SET id = :newUserId WHERE id = :oldUserId AND groupId = :groupId")
    suspend fun updateUserIdForLocalPlayer(oldUserId: String, groupId: String, newUserId: String)

    @Transaction
    suspend fun reconcileLegacyLocalUserIds(profileUserId: String) {
        val legacyUsers = getLegacyLocalUsers(profileUserId)
        for (user in legacyUsers) {
            val existingUser = getUserByIdAndGroup(profileUserId, user.groupId)
            if (existingUser == null) {
                updateUserIdForLocalPlayer(user.id, user.groupId, profileUserId)
            } else {
                deleteUser(user)
            }
        }
    }

    // --- ATOMIC TRANSACTIONAL OPERATIONS (Task 1) ---

    @Transaction
    suspend fun updateGroupWithSync(group: Group, users: List<User>, syncEvent: SyncEvent) {
        updateGroup(group)
        users.forEach { insertUser(it) }
        insertSyncEvent(syncEvent)
    }

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

    /**
     * Atomically updates a user and logs the sync event.
     */
    @Transaction
    suspend fun updateUserWithSync(user: User, syncEvent: SyncEvent) {
        insertUser(user)
        insertSyncEvent(syncEvent)
    }
}
