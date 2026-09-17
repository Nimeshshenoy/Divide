package com.example.divide.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Repository that manages data persistence using Room.
 */
object LocalRepository {
    private var dao: ExpenseDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Reactive flow of all events from the database
    private var _eventsFlow: StateFlow<List<ExpenseEvent>>? = null
    val events: StateFlow<List<ExpenseEvent>>
        get() = _eventsFlow ?: throw IllegalStateException("LocalRepository must be initialized with a DAO")

    // Reactive flow of the local user's profile
    private var _profileFlow: StateFlow<Profile>? = null
    val profile: StateFlow<Profile>
        get() = _profileFlow ?: throw IllegalStateException("LocalRepository must be initialized with a DAO")

    /**
     * Call this once at app startup to link the database.
     */
    fun initialize(expenseDao: ExpenseDao) {
        dao = expenseDao
        _eventsFlow = expenseDao.getAllEvents().stateIn(
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

    fun addEvent(event: ExpenseEvent) {
        scope.launch {
            dao?.insertEvent(event)
        }
    }

    fun updateEvent(event: ExpenseEvent) {
        scope.launch {
            dao?.updateEvent(event)
        }
    }

    fun updateProfile(name: String) {
        scope.launch {
            dao?.insertProfile(Profile(name = name))
        }
    }

    suspend fun getEvent(eventId: String): ExpenseEvent? {
        return dao?.getEventById(eventId)
    }
}
