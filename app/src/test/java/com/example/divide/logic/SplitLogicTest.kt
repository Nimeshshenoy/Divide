package com.example.divide.logic

import com.example.divide.data.Expense
import com.example.divide.data.Group
import com.example.divide.data.TripWithDetails
import com.example.divide.data.User
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLogicTest {

    @Test
    fun testBasicSplit() {
        val groupId = "group1"
        val userA = User(id = "A", name = "A", groupId = groupId)
        val userB = User(id = "B", name = "B", groupId = groupId)
        val userC = User(id = "C", name = "C", groupId = groupId)
        
        val tripWithDetails = TripWithDetails(
            group = Group(id = groupId, name = "Test"),
            users = listOf(userA, userB, userC),
            expenses = listOf(
                Expense(
                    groupId = groupId,
                    name = "Lunch",
                    amount = 30.0,
                    payerId = userA.id,
                    participantIds = listOf(userA.id, userB.id, userC.id)
                )
            )
        )
        
        val balances = SplitLogic.calculateBalances(tripWithDetails)
        
        // A paid 30, share is 10. A is owed 20.
        // B and C each owe 10.
        
        val balanceA = balances.find { it.user.id == userA.id }?.amountRupees ?: 0.0
        val balanceB = balances.find { it.user.id == userB.id }?.amountRupees ?: 0.0
        val balanceC = balances.find { it.user.id == userC.id }?.amountRupees ?: 0.0
        
        assertEquals(20.0, balanceA, 0.001)
        assertEquals(-10.0, balanceB, 0.001)
        assertEquals(-10.0, balanceC, 0.001)
    }

    @Test
    fun testPartialSplit() {
        val groupId = "group2"
        val userA = User(id = "A", name = "A", groupId = groupId)
        val userB = User(id = "B", name = "B", groupId = groupId)
        val userC = User(id = "C", name = "C", groupId = groupId)
        
        val tripWithDetails = TripWithDetails(
            group = Group(id = groupId, name = "Test"),
            users = listOf(userA, userB, userC),
            expenses = listOf(
                Expense(
                    groupId = groupId,
                    name = "Drink",
                    amount = 20.0,
                    payerId = userA.id,
                    participantIds = listOf(userA.id, userB.id) // C is not sharing
                )
            )
        )
        
        val balances = SplitLogic.calculateBalances(tripWithDetails)
        
        val balanceA = balances.find { it.user.id == userA.id }?.amountRupees ?: 0.0
        val balanceB = balances.find { it.user.id == userB.id }?.amountRupees ?: 0.0
        val balanceC = balances.find { it.user.id == userC.id }?.amountRupees ?: 0.0
        
        assertEquals(10.0, balanceA, 0.001)
        assertEquals(-10.0, balanceB, 0.001)
        assertEquals(0.0, balanceC, 0.001)
    }
}
