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
        scope.launch {
            reconcileLocalUserIdentities(expenseDao)
        }
    }

    private suspend fun reconcileLocalUserIdentities(expenseDao: ExpenseDao) {
        val currentProfile = profile.value
        expenseDao.reconcileLegacyLocalUserIds(currentProfile.userId)
    }

    // --- ATOMIC WRITE LOGIC ---

    fun addTrip(group: Group, creators: List<User>) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = group.id,
                authorId = profile.value.userId, // Persistent distributed identity
                type = "ADD_TRIP",
                payload = gson.toJson(mapOf("group" to group, "users" to creators))
            )
            // Store the ID of the event that created this trip
            val tripWithEvent = group.copy(lastEventId = syncEvent.eventId)
            val usersWithEvent = creators.map { it.copy(lastEventId = syncEvent.eventId) }
            
            dao?.insertTripWithSync(tripWithEvent, usersWithEvent, syncEvent)
        }
    }

    fun updateTrip(group: Group, users: List<User>) {
        scope.launch {
            val syncEvent = SyncEvent(
                groupId = group.id,
                authorId = profile.value.userId,
                type = "UPDATE_GROUP",
                payload = gson.toJson(mapOf("group" to group, "users" to users))
            )
            val groupWithEvent = group.copy(lastEventId = syncEvent.eventId)
            val usersWithEvent = users.map { it.copy(lastEventId = syncEvent.eventId) }
            dao?.updateGroupWithSync(groupWithEvent, usersWithEvent, syncEvent)
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
     * Updates the local user's name and UPI ID across the profile and all active trips.
     * Logs a SyncEvent for each trip so friends stay informed.
     */
    fun updateProfile(newName: String, newUpiId: String? = profile.value.upiId) {
        val oldProfile = profile.value

        scope.launch {
            val formattedUpi = newUpiId?.trim()?.ifEmpty { null }
            // 1. Update the primary profile
            val updatedProfile = oldProfile.copy(
                name = newName,
                upiId = formattedUpi
            )
            dao?.insertProfile(updatedProfile)

            // 2. Propagate the change to all trips the user is part of
            val allTrips = trips.value
            allTrips.forEach { tripDetails ->
                val localUser = tripDetails.users.find { it.isLocalPlayer }
                if (localUser != null) {
                    val updatedUser = localUser.copy(
                        name = newName,
                        upiId = formattedUpi
                    )
                    
                    val syncEvent = SyncEvent(
                        groupId = tripDetails.group.id,
                        authorId = oldProfile.userId,
                        type = "UPDATE_USER",
                        payload = gson.toJson(updatedUser)
                    )
                    
                    val userWithEvent = updatedUser.copy(lastEventId = syncEvent.eventId)
                    dao?.updateUserWithSync(userWithEvent, syncEvent)
                }
            }
        }
    }

    /**
     * Called when joining a trip or connecting to a peer host.
     * Logs a JOIN_TRIP SyncEvent with the local player's real name & upiId.
     */
    fun joinTrip(groupId: String) {
        val currentProfile = profile.value
        val localUser = User(
            id = currentProfile.userId,
            name = currentProfile.name,
            groupId = groupId,
            isLocalPlayer = true,
            upiId = currentProfile.upiId
        )

        scope.launch {
            val syncEvent = SyncEvent(
                groupId = groupId,
                authorId = currentProfile.userId,
                type = "JOIN_TRIP",
                payload = gson.toJson(localUser)
            )

            val userWithEvent = localUser.copy(lastEventId = syncEvent.eventId)
            dao?.updateUserWithSync(userWithEvent, syncEvent)
        }
    }
}
