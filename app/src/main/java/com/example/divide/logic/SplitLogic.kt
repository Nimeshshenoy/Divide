package com.example.divide.logic

import com.example.divide.data.TripWithDetails
import com.example.divide.data.User
import kotlin.math.roundToLong

/**
 * Result of a split calculation for a specific user in integer paise.
 * Positive amountPaise: User is owed money (creditor)
 * Negative amountPaise: User owes money (debtor)
 */
data class Balance(
    val user: User,
    val amountPaise: Long
) {
    val amountRupees: Double
        get() = amountPaise / 100.0
}

/**
 * Direct settlement transaction between two users in integer paise.
 */
data class DebtSettlement(
    val fromUser: User,
    val toUser: User,
    val amountPaise: Long
) {
    val amountRupees: Double
        get() = amountPaise / 100.0
}

/**
 * Settlement status formatted for a specific user view.
 */
sealed class UserSettlementStatus {
    data class Owes(val toUser: User, val amountPaise: Long) : UserSettlementStatus() {
        val amountRupees: Double get() = amountPaise / 100.0
    }
    data class Owed(val fromUser: User, val amountPaise: Long) : UserSettlementStatus() {
        val amountRupees: Double get() = amountPaise / 100.0
    }
    object SettledUp : UserSettlementStatus()
}

/**
 * The core calculation engine for splitting expenses and computing settlements.
 */
object SplitLogic {

    /**
     * Calculates the net balance in integer paise for each user in a trip.
     */
    fun calculateBalances(tripDetails: TripWithDetails): List<Balance> {
        val balanceMap = tripDetails.users.associate { it.id to 0L }.toMutableMap()
        val activeExpenses = tripDetails.expenses.filter { !it.isDeleted }

        for (expense in activeExpenses) {
            if (expense.participantIds.isEmpty()) continue

            val totalPaise = (expense.amount * 100).roundToLong()
            val numParticipants = expense.participantIds.size.toLong()
            val sharePerParticipant = totalPaise / numParticipants
            val remainder = totalPaise % numParticipants

            // Credit the Payer
            if (balanceMap.containsKey(expense.payerId)) {
                balanceMap[expense.payerId] = balanceMap[expense.payerId]!! + totalPaise
            }

            // Debit Participants
            expense.participantIds.forEachIndexed { index, participantId ->
                if (balanceMap.containsKey(participantId)) {
                    val extraPaise = if (index < remainder.toInt()) 1L else 0L
                    val participantShare = sharePerParticipant + extraPaise
                    balanceMap[participantId] = balanceMap[participantId]!! - participantShare
                }
            }
        }

        return tripDetails.users.map { user ->
            Balance(user, balanceMap[user.id] ?: 0L)
        }
    }

    /**
     * Greedy algorithm to minimize total number of settlement transactions.
     */
    fun calculateSettlements(balances: List<Balance>): List<DebtSettlement> {
        val debtors = balances.filter { it.amountPaise < 0 }
            .map { MutableBalance(it.user, -it.amountPaise) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val creditors = balances.filter { it.amountPaise > 0 }
            .map { MutableBalance(it.user, it.amountPaise) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val settlements = mutableListOf<DebtSettlement>()

        var debtorIdx = 0
        var creditorIdx = 0

        while (debtorIdx < debtors.size && creditorIdx < creditors.size) {
            val debtor = debtors[debtorIdx]
            val creditor = creditors[creditorIdx]

            val payment = minOf(debtor.amount, creditor.amount)

            if (payment > 0) {
                settlements.add(
                    DebtSettlement(
                        fromUser = debtor.user,
                        toUser = creditor.user,
                        amountPaise = payment
                    )
                )

                debtor.amount -= payment
                creditor.amount -= payment
            }

            if (debtor.amount == 0L) debtorIdx++
            if (creditor.amount == 0L) creditorIdx++
        }

        return settlements
    }

    /**
     * Prepares settlements specifically relevant for the local user.
     */
    fun getLocalUserSettlements(
        tripDetails: TripWithDetails,
        localUserId: String? = null
    ): List<UserSettlementStatus> {
        val localUser = tripDetails.users.firstOrNull {
            if (localUserId != null) it.id == localUserId else it.isLocalPlayer
        } ?: tripDetails.users.firstOrNull() ?: return listOf(UserSettlementStatus.SettledUp)

        val balances = calculateBalances(tripDetails)
        val settlements = calculateSettlements(balances)

        val userSettlements = mutableListOf<UserSettlementStatus>()

        for (settlement in settlements) {
            if (settlement.fromUser.id == localUser.id) {
                userSettlements.add(UserSettlementStatus.Owes(settlement.toUser, settlement.amountPaise))
            } else if (settlement.toUser.id == localUser.id) {
                userSettlements.add(UserSettlementStatus.Owed(settlement.fromUser, settlement.amountPaise))
            }
        }

        return if (userSettlements.isEmpty()) listOf(UserSettlementStatus.SettledUp) else userSettlements
    }

    private data class MutableBalance(val user: User, var amount: Long)
}
