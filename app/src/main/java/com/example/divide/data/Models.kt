package com.example.divide.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * [Group] represents the metadata of a shared trip.
 * This table is normalized to avoid storing large JSON blobs.
 */
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * [User] represents a participant.
 * Users are stored in their own table to support multi-group memberships
 * and global profile settings.
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["groupId"])]
)
data class User(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val groupId: String, // Links this participant record to a specific group
    val isLocalPlayer: Boolean = false // Identifies the device owner
)

/**
 * [Expense] represents a single transaction within a group.
 * It uses ForeignKeys (conceptually) to relate back to the Group.
 */
@Entity(
    tableName = "expenses",
    indices = [Index(value = ["groupId"])]
)
data class Expense(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val groupId: String, // The trip this expense belongs to
    val name: String,
    val amount: Double,
    val payerId: String, // ID of the User who paid
    val participantIds: List<String>, // List of User IDs sharing the cost
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * [SyncEvent] is the core of our P2P architecture.
 * Instead of syncing whole tables, we sync these "Action" records.
 * If 4 phones have the same list of SyncEvents, they will arrive at the same state.
 */
@Entity(tableName = "sync_events")
data class SyncEvent(
    @PrimaryKey
    val eventId: String = UUID.randomUUID().toString(), // Must be unique across all devices
    val groupId: String,
    val authorId: String, // Who created this action
    val type: String, // e.g., "ADD_EXPENSE", "ADD_USER", "UPDATE_GROUP"
    val payload: String, // JSON representation of the data (User, Expense, or Group)
    val createdAt: Long = System.currentTimeMillis(),
    val signature: String? = null // Reserved for future cryptographic security
)
