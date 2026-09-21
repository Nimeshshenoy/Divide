package com.example.divide.data

import com.example.divide.sync.EventProcessor
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository that manages data persistence and ensures atomicity for P2P sync.
 */
object LocalRepository {
    private var dao: ExpenseDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // The logic engine for processing incoming P2P sync events
    private var _eventProcessor: EventProcessor? = null
    val eventProcessor: EventProcessor
        get() = _eventProcessor ?: throw IllegalStateException("LocalRepository must be initialized")

    private var _tripsFlow: StateFlow<List<TripWithDetails>>? = null
    val trips: StateFlow<List<TripWithDetails>>
        get() = _tripsFlow ?: throw IllegalStateException("LocalRepository must be initialized")

    private var _profileFlow: StateFlow<Profile>? = null
    val profile: StateFlow<Profile>
        get() = _profileFlow ?: throw IllegalStateException("LocalRepository must be initialized")

    fun initialize(expenseDao: ExpenseDao) {
        dao = expenseDao
        _eventProcessor = EventProcessor(expenseDao)
        _tripsFlow = expenseDao.getAllTripsWithDetails().stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        _profileFlow = expenseDao.getProfile()
            .map { it ?: Profile() }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = Profile()
            )
    }

    // --- ATOMIC WRITE LOGIC ---

    fun addTrip(group: Group, creators: List<User>) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = group.id,
                authorId = creators.firstOrNull { it.isLocalPlayer }?.id ?: "local",
                type = "ADD_TRIP",
                payload = gson.toJson(mapOf("group" to group, "users" to creators))
            )
            // Call atomic transaction in DAO
            dao?.insertTripWithSync(group, creators, syncEvent)
        }
    }

    fun addExpense(expense: Expense) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = expense.groupId,
                authorId = profile.value.userId, // Using distributed identity
                type = "ADD_EXPENSE",
                payload = gson.toJson(expense)
            )
            dao?.insertExpenseWithSync(expense, syncEvent)
        }
    }

    fun updateExpense(expense: Expense) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = expense.groupId,
                authorId = profile.value.userId, // Using distributed identity
                type = "UPDATE_EXPENSE",
                payload = gson.toJson(expense)
            )
            dao?.updateExpenseWithSync(expense, syncEvent)
        }
    }

    fun deleteExpense(expense: Expense) {
        scope.launch {
            // Task 4: Soft Delete (Tombstone)
            val deletedExpense = expense.copy(isDeleted = true)
            val syncEvent = SyncEvent(
                groupId = expense.groupId,
                authorId = profile.value.userId, // Using distributed identity
                type = "DELETE_EXPENSE",
                payload = gson.toJson(expense.id)
            )
            // Reusing update with sync for the soft delete
            dao?.updateExpenseWithSync(deletedExpense, syncEvent)
        }
    }

    suspend fun getTrip(tripId: String): TripWithDetails? {
        return dao?.getTripWithDetailsById(tripId)
    }

    /**
     * Updates the local user's name across the profile and all active trips.
     * Logs a SyncEvent for each trip so friends stay informed.
     */
    fun updateProfile(newName: String) {
        val oldProfile = profile.value
        if (oldProfile.name == newName) return

        scope.launch {
            // 1. Update the primary profile
            dao?.insertProfile(oldProfile.copy(name = newName))

            // 2. Propagate the name change to all trips the user is part of
            val allTrips = trips.value
            allTrips.forEach { tripDetails ->
                val localUser = tripDetails.users.find { it.isLocalPlayer }
                if (localUser != null) {
                    val updatedUser = localUser.copy(name = newName)
                    dao?.insertUser(updatedUser)

                    // 3. Log a SyncEvent for this specific trip
                    dao?.insertSyncEvent(
                        SyncEvent(
                            groupId = tripDetails.group.id,
                            authorId = oldProfile.userId,
                            type = "UPDATE_USER_NAME",
                            payload = gson.toJson(updatedUser)
                        )
                    )
                }
            }
        }
    }
}
