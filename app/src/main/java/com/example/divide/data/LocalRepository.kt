package com.example.divide.data

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

    private var _tripsFlow: StateFlow<List<TripWithDetails>>? = null
    val trips: StateFlow<List<TripWithDetails>>
        get() = _tripsFlow ?: throw IllegalStateException("LocalRepository must be initialized")

    private var _profileFlow: StateFlow<Profile>? = null
    val profile: StateFlow<Profile>
        get() = _profileFlow ?: throw IllegalStateException("LocalRepository must be initialized")

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
                authorId = profile.value.id.toString(),
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
                authorId = profile.value.id.toString(),
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
                authorId = profile.value.id.toString(),
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

    fun updateProfile(name: String) {
        scope.launch {
            dao?.insertProfile(Profile(name = name))
        }
    }
}
