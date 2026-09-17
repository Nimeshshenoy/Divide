package com.example.divide.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the local user's profile settings.
 * We use a fixed ID of 0 because there is only one local profile.
 */
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey
    val id: Int = 0,
    val name: String = "Me"
)
