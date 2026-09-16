package com.example.divide.logic

import com.example.divide.data.Expense
import com.example.divide.data.ExpenseEvent
import com.example.divide.data.User
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLogicTest {

    @Test
    fun testBasicSplit() {
        val userA = User(name = "A")
        val userB = User(name = "B")
        val userC = User(name = "C")
        
        val event = ExpenseEvent(
            name = "Test",
            users = listOf(userA, userB, userC),
            expenses = listOf(
                Expense(
                    name = "Lunch",
                    amount = 30.0,
                    payerId = userA.id,
                    participantIds = listOf(userA.id, userB.id, userC.id)
                )
            )
        )
        
        val balances = SplitLogic.calculateBalances(event)
        
        // A paid 30, share is 10. A is owed 20.
        // B and C each owe 10.
        
        val balanceA = balances.find { it.user.id == userA.id }?.amount ?: 0.0
        val balanceB = balances.find { it.user.id == userB.id }?.amount ?: 0.0
        val balanceC = balances.find { it.user.id == userC.id }?.amount ?: 0.0
        
        assertEquals(20.0, balanceA, 0.001)
        assertEquals(-10.0, balanceB, 0.001)
        assertEquals(-10.0, balanceC, 0.001)
    }

    @Test
    fun testPartialSplit() {
        val userA = User(name = "A")
        val userB = User(name = "B")
        val userC = User(name = "C")
        
        val event = ExpenseEvent(
            name = "Test",
            users = listOf(userA, userB, userC),
            expenses = listOf(
                Expense(
                    name = "Drink",
                    amount = 20.0,
                    payerId = userA.id,
                    participantIds = listOf(userA.id, userB.id) // C is not sharing
                )
            )
        )
        
        val balances = SplitLogic.calculateBalances(event)
        
        val balanceA = balances.find { it.user.id == userA.id }?.amount ?: 0.0
        val balanceB = balances.find { it.user.id == userB.id }?.amount ?: 0.0
        val balanceC = balances.find { it.user.id == userC.id }?.amount ?: 0.0
        
        assertEquals(10.0, balanceA, 0.001)
        assertEquals(-10.0, balanceB, 0.001)
        assertEquals(0.0, balanceC, 0.001)
    }
}
