package com.example.divide.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * [TripWithDetails] is a "Read Model" or "POJO" (Plain Old Java Object).
 * It isn't a database table itself. Instead, Room uses it to automatically
 * join the data from Groups, Users, and Expenses tables for the UI.
 */
data class TripWithDetails(
    @Embedded val group: Group,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val users: List<User>,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val expenses: List<Expense>
)
