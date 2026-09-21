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
            // Store the ID of the event that created this trip
            val tripWithEvent = group.copy(lastEventId = syncEvent.eventId)
            val usersWithEvent = creators.map { it.copy(lastEventId = syncEvent.eventId) }
            
            dao?.insertTripWithSync(tripWithEvent, usersWithEvent, syncEvent)
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
            // Track which event created this expense
            val expenseWithEvent = expense.copy(lastEventId = syncEvent.eventId)
            dao?.insertExpenseWithSync(expenseWithEvent, syncEvent)
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
            // Track which event updated this expense
            val expenseWithEvent = expense.copy(lastEventId = syncEvent.eventId)
            dao?.updateExpenseWithSync(expenseWithEvent, syncEvent)
        }
    }

    fun deleteExpense(expense: Expense) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = expense.groupId,
                authorId = profile.value.userId, // Using distributed identity
                type = "DELETE_EXPENSE",
                payload = gson.toJson(expense.id)
            )
            // Task 4: Soft Delete (Tombstone)
            val deletedExpense = expense.copy(
                isDeleted = true,
                lastEventId = syncEvent.eventId // Track the delete event
            )
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
                    val syncEvent = SyncEvent(
                        groupId = tripDetails.group.id,
                        authorId = oldProfile.userId,
                        type = "UPDATE_USER_NAME",
                        payload = gson.toJson(localUser.copy(name = newName))
                    )
                    
                    val updatedUser = localUser.copy(
                        name = newName,
                        lastEventId = syncEvent.eventId
                    )
                    
                    dao?.updateUserWithSync(updatedUser, syncEvent)
                }
            }
        }
    }
}
