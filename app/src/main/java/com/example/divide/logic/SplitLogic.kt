package com.example.divide.logic

import com.example.divide.data.Trip
import com.example.divide.data.User

/**
 * Result of a split calculation for a specific user.
 */
data class Balance(
    val user: User,
    val amount: Double // Positive means they are owed (payer), negative means they owe (debtor)
)

/**
 * The core calculation engine for splitting expenses.
 */
object SplitLogic {
    /**
     * Calculates the net balance for each user in a trip.
     * 
     * Logic:
     * 1. Start every user at 0.0 balance.
     * 2. For each individual expense:
     *    - The Payer is credited the full amount (+ amount).
     *    - Each participant is debited their share (- amount / number of participants).
     * 3. The sum for each user results in their net position.
     */
    fun calculateBalances(trip: Trip): List<Balance> {
        // Initialize balances for all users in the trip to 0
        val balanceMap = trip.users.associate { it.id to 0.0 }.toMutableMap()

        for (expense in trip.expenses) {
            // Avoid division by zero if no one is sharing
            if (expense.participantIds.isEmpty()) continue

            // Equal split amount for each participant
            val share = expense.amount / expense.participantIds.size
            
            // Credit the Payer: They spent this money, so the system 'owes' it back to them
            balanceMap[expense.payerId] = (balanceMap[expense.payerId] ?: 0.0) + expense.amount
            
            // Debit Participants: Each person sharing the expense 'owes' their share to the system
            for (participantId in expense.participantIds) {
                balanceMap[participantId] = (balanceMap[participantId] ?: 0.0) - share
            }
        }

        // Convert the map back to a list of Balance objects for the UI
        return trip.users.map { user ->
            Balance(user, balanceMap[user.id] ?: 0.0)
        }
    }
}
