package com.example.divide.data

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository that manages data persistence using Room and prepares data for P2P sync.
 */
object LocalRepository {
    private var dao: ExpenseDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // Reactive flow of all trips combined with their users and expenses
    private var _tripsFlow: StateFlow<List<TripWithDetails>>? = null
    val trips: StateFlow<List<TripWithDetails>>
        get() = _tripsFlow ?: throw IllegalStateException("LocalRepository must be initialized with a DAO")

    // Reactive flow of the local user's profile
    private var _profileFlow: StateFlow<Profile>? = null
    val profile: StateFlow<Profile>
        get() = _profileFlow ?: throw IllegalStateException("LocalRepository must be initialized with a DAO")

    /**
     * Call this once at app startup to link the database.
     */
    fun initialize(expenseDao: ExpenseDao) {
        dao = expenseDao
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

    // --- WRITE LOGIC WITH SYNC LOGGING ---

    /**
     * Creates a new trip.
     * Also logs an "ADD_TRIP" event in the sync ledger.
     */
    fun addTrip(group: Group, creators: List<User>) {
        scope.launch {
            // 1. Save data to local tables
            dao?.insertGroup(group)
            creators.forEach { dao?.insertUser(it) }

            // 2. Log sync event (Payload includes both group and its initial users)
            val payload = gson.toJson(mapOf("group" to group, "users" to creators))
            dao?.insertSyncEvent(
                SyncEvent(
                    groupId = group.id,
                    authorId = creators.firstOrNull { it.isLocalPlayer }?.id ?: "local",
                    type = "ADD_TRIP",
                    payload = payload
                )
            )
        }
    }

    /**
     * Adds an expense to a specific trip.
     * Also logs an "ADD_EXPENSE" event.
     */
    fun addExpense(expense: Expense) {
        scope.launch {
            dao?.insertExpense(expense)

            dao?.insertSyncEvent(
                SyncEvent(
                    groupId = expense.groupId,
                    authorId = profile.value.id.toString(), // Simplified author tracking
                    type = "ADD_EXPENSE",
                    payload = gson.toJson(expense)
                )
            )
        }
    }

    /**
     * Updates an existing expense.
     */
    fun updateExpense(expense: Expense) {
        scope.launch {
            dao?.updateExpense(expense)

            dao?.insertSyncEvent(
                SyncEvent(
                    groupId = expense.groupId,
                    authorId = profile.value.id.toString(),
                    type = "UPDATE_EXPENSE",
                    payload = gson.toJson(expense)
                )
            )
        }
    }

    /**
     * Deletes an expense.
     */
    fun deleteExpense(expense: Expense) {
        scope.launch {
            dao?.deleteExpense(expense)

            dao?.insertSyncEvent(
                SyncEvent(
                    groupId = expense.groupId,
                    authorId = profile.value.id.toString(),
                    type = "DELETE_EXPENSE",
                    payload = gson.toJson(expense.id) // Just send the ID to delete
                )
            )
        }
    }

    suspend fun getTrip(tripId: String): TripWithDetails? {
        return dao?.getTripWithDetailsById(tripId)
    }

    fun updateProfile(name: String) {
        scope.launch {
            dao?.insertProfile(Profile(name = name))
        }
    }
}
