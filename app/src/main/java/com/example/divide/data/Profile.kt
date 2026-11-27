package com.example.divide.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents the local user's profile settings.
 * [id] is the local database primary key (always 0).
 * [userId] is the globally unique distributed identity for P2P sync.
 */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey
    val id: Int = 0,
    val userId: String = UUID.randomUUID().toString(), // Distributed Identity
    val name: String = "Me",
    val upiId: String? = null,
    val isProfileConfigured: Boolean = false
)
