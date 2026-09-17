package com.example.divide.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a person participating in expenses.
 */
data class User(
    val id: String = UUID.randomUUID().toString(), // Unique ID for identification
    val name: String,
    val isLocalPlayer: Boolean = false // True for the user holding this device
)

/**
 * Represents a single financial transaction (e.g., "Lunch").
 */
data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "Lunch"
    val amount: Double, // The total cost paid
    val payerId: String, // The ID of the User who paid
    val participantIds: List<String> // IDs of users who are sharing this specific cost
)

/**
 * Represents a collection of expenses (e.g., "Udaipur Trip").
 * This acts as a container for all participants and their shared expenses.
 * Annotated with @Entity for Room persistence.
 */
@Entity(tableName = "events")
data class ExpenseEvent(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "Udaipur Trip"
    val users: List<User> = emptyList(), // All friends added to this trip
    val expenses: List<Expense> = emptyList(), // All individual costs (Lunch, Dinner, etc.)
    val isSettled: Boolean = false // Whether the trip accounts have been closed
)
