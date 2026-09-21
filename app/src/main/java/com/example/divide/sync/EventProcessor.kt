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

    private suspend fun handleAddTrip(event: SyncEvent): Boolean {
        // Payload for ADD_TRIP contains a Map with "group" and "users"
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val data: Map<String, Any> = gson.fromJson(event.payload, type)
        
        // In a real app, we'd use a more robust parsing strategy than Map<String, Any>
        // but for now, we'll parse the core bits.
        val groupJson = gson.toJson(data["group"])
        val group = gson.fromJson(groupJson, Group::class.java)
        
        val usersJson = gson.toJson(data["users"])
        val usersType = object : TypeToken<List<User>>() {}.type
        val users: List<User> = gson.fromJson(usersJson, usersType)

        dao.insertTripWithSync(group, users, event)
        return true
    }

    private suspend fun handleAddExpense(event: SyncEvent): Boolean {
        val expense = gson.fromJson(event.payload, Expense::class.java)
        dao.insertExpenseWithSync(expense, event)
        return true
    }

    private suspend fun handleUpdateExpense(event: SyncEvent): Boolean {
        val incomingExpense = gson.fromJson(event.payload, Expense::class.java)
        val localExpense = dao.getExpenseById(incomingExpense.id)

        // Conflict Resolution: Deterministic Tie-Breaking
        // If local version exists, we only update if the incoming event is "newer"
        // based on SyncEvent.createdAt and lexicographical eventId.
        if (localExpense != null) {
            // Note: In a full event-sourcing model, we'd find the SyncEvent that 
            // created the local state and compare timestamps.
            // For now, we'll assume the incoming event represents the desired state.
            dao.updateExpenseWithSync(incomingExpense, event)
        } else {
            // Treat as an add if we don't have it yet
            dao.insertExpenseWithSync(incomingExpense, event)
        }
        return true
    }

    private suspend fun handleDeleteExpense(event: SyncEvent): Boolean {
        val expenseId = gson.fromJson(event.payload, String::class.java)
        val localExpense = dao.getExpenseById(expenseId)
        
        if (localExpense != null) {
            val deletedExpense = localExpense.copy(isDeleted = true)
            dao.updateExpenseWithSync(deletedExpense, event)
            return true
        }
        return false
    }

    private suspend fun handleUpdateUserName(event: SyncEvent): Boolean {
        val updatedUser = gson.fromJson(event.payload, User::class.java)
        dao.insertUser(updatedUser)
        dao.insertSyncEvent(event)
        return true
    }
}
