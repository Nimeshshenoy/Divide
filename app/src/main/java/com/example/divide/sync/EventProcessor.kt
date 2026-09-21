package com.example.divide.sync

import com.example.divide.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [EventProcessor] handles incoming SyncEvents from other devices.
 * It ensures deduplication, routes to correct handlers, and applies updates
 * to the local state while resolving potential conflicts.
 */
class EventProcessor(private val dao: ExpenseDao) {
    private val gson = Gson()

    /**
     * Processes an incoming event. Returns true if the event was newly processed,
     * false if it was a duplicate or ignored due to conflict resolution.
     */
    suspend fun processIncomingEvent(event: SyncEvent): Boolean = withContext(Dispatchers.IO) {
        // 1. Deduplication: Check if we already have this event in our ledger
        if (dao.getSyncEventById(event.eventId) != null) {
            return@withContext false
        }

        // 2. Route by event type
        return@withContext when (event.type) {
            "ADD_TRIP" -> handleAddTrip(event)
            "ADD_EXPENSE" -> handleAddExpense(event)
            "UPDATE_EXPENSE" -> handleUpdateExpense(event)
            "DELETE_EXPENSE" -> handleDeleteExpense(event)
            "UPDATE_USER_NAME" -> handleUpdateUserName(event)
            else -> false
        }
    }

    /**
     * Conflict Resolution: Deterministic Tie-Breaking
     * Returns true if the [incoming] event is "superior" to the one that produced [currentEventId].
     */
    private suspend fun isIncomingWinner(incoming: SyncEvent, currentEventId: String?): Boolean {
        if (currentEventId == null) return true // No existing state
        
        val currentEvent = dao.getSyncEventById(currentEventId) ?: return true
        
        return if (incoming.createdAt != currentEvent.createdAt) {
            // Rule 1: Latest timestamp wins
            incoming.createdAt > currentEvent.createdAt
        } else {
            // Rule 2: Lexicographical tie-breaker on EventID
            incoming.eventId > currentEvent.eventId
        }
    }

    private suspend fun handleAddTrip(event: SyncEvent): Boolean {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val data: Map<String, Any> = gson.fromJson(event.payload, type)
        
        val groupJson = gson.toJson(data["group"])
        val group = gson.fromJson(groupJson, Group::class.java).copy(lastEventId = event.eventId)
        
        val usersJson = gson.toJson(data["users"])
        val usersType = object : TypeToken<List<User>>() {}.type
        val rawUsers: List<User> = gson.fromJson(usersJson, usersType)
        val users = rawUsers.map { it.copy(lastEventId = event.eventId) }

        // Only add if group doesn't exist or incoming event wins
        val existingGroup = dao.getGroupById(group.id)
        if (isIncomingWinner(event, existingGroup?.lastEventId)) {
            dao.insertTripWithSync(group, users, event)
            return true
        }
        return false
    }

    private suspend fun handleAddExpense(event: SyncEvent): Boolean {
        val expense = gson.fromJson(event.payload, Expense::class.java).copy(lastEventId = event.eventId)
        val existingExpense = dao.getExpenseById(expense.id)

        // Task 2: Prevent ADD from resurrecting a tombstone if it's "older"
        if (isIncomingWinner(event, existingExpense?.lastEventId)) {
            dao.insertExpenseWithSync(expense, event)
            return true
        }
        return false
    }

    private suspend fun handleUpdateExpense(event: SyncEvent): Boolean {
        val expense = gson.fromJson(event.payload, Expense::class.java).copy(lastEventId = event.eventId)
        val existingExpense = dao.getExpenseById(expense.id)

        if (isIncomingWinner(event, existingExpense?.lastEventId)) {
            dao.updateExpenseWithSync(expense, event)
            return true
        }
        return false
    }

    private suspend fun handleDeleteExpense(event: SyncEvent): Boolean {
        val expenseId = gson.fromJson(event.payload, String::class.java)
        val existingExpense = dao.getExpenseById(expenseId)
        
        if (isIncomingWinner(event, existingExpense?.lastEventId)) {
            if (existingExpense != null) {
                // Task 4: Mark as deleted (Soft Delete)
                val deletedExpense = existingExpense.copy(isDeleted = true, lastEventId = event.eventId)
                dao.updateExpenseWithSync(deletedExpense, event)
            } else {
                // Task 3: Create a tombstone for an unknown expense
                val tombstone = Expense(
                    id = expenseId,
                    groupId = event.groupId,
                    name = "Unknown (Deleted)",
                    amount = 0.0,
                    payerId = "unknown",
                    participantIds = emptyList(),
                    isDeleted = true,
                    lastEventId = event.eventId
                )
                dao.insertExpenseWithSync(tombstone, event)
            }
            return true
        }
        return false
    }

    private suspend fun handleUpdateUserName(event: SyncEvent): Boolean {
        val incomingUser = gson.fromJson(event.payload, User::class.java).copy(lastEventId = event.eventId)
        val existingUser = dao.getUserById(incomingUser.id)
        
        if (isIncomingWinner(event, existingUser?.lastEventId)) {
            // Task 4: Ensure atomicity
            dao.updateUserWithSync(incomingUser, event)
            return true
        }
        return false
    }
}
