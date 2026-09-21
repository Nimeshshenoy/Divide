package com.example.divide.sync

import com.example.divide.data.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EventProcessorTest {

    @Test
    fun `test deduplication - same event processed twice should return false`() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val processor = EventProcessor(fakeDao)
        val gson = Gson()
        
        val groupId = UUID.randomUUID().toString()
        val expense = Expense(id = "exp1", groupId = groupId, name = "Lunch", amount = 100.0, payerId = "p1", participantIds = listOf("p1"))
        
        val event = SyncEvent(
            eventId = "unique_event_1",
            groupId = groupId,
            authorId = "author1",
            type = "ADD_EXPENSE",
            payload = gson.toJson(expense)
        )

        // First time should succeed
        assertTrue(processor.processIncomingEvent(event))
        
        // Second time should be ignored
        assertFalse(processor.processIncomingEvent(event))
    }

    @Test
    fun `test routing - add expense event updates local state`() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val processor = EventProcessor(fakeDao)
        val gson = Gson()
        
        val groupId = UUID.randomUUID().toString()
        val expenseId = "exp_to_add"
        val expense = Expense(id = expenseId, groupId = groupId, name = "Dinner", amount = 200.0, payerId = "p1", participantIds = listOf("p1"))
        
        val event = SyncEvent(
            eventId = "event_2",
            groupId = groupId,
            authorId = "author1",
            type = "ADD_EXPENSE",
            payload = gson.toJson(expense)
        )

        processor.processIncomingEvent(event)
        
        // Verify that the expense was added to the "state" table (via fake dao)
        assertTrue(fakeDao.expenses.any { it.id == expenseId })
    }

    // --- FAKE DAO FOR TESTING ---
    
    class FakeExpenseDao : ExpenseDao {
        val events = mutableListOf<SyncEvent>()
        val expenses = mutableListOf<Expense>()
        val groups = mutableListOf<Group>()
        val users = mutableListOf<User>()

        override fun getAllTripsWithDetails(): Flow<List<TripWithDetails>> = emptyFlow()
        override suspend fun getTripWithDetailsById(groupId: String): TripWithDetails? = null
        override fun getProfile(): Flow<Profile?> = emptyFlow()
        override suspend fun insertGroup(group: Group) { groups.add(group) }
        override suspend fun insertUser(user: User) { users.add(user) }
        override suspend fun insertExpense(expense: Expense) { expenses.add(expense) }
        override suspend fun insertSyncEvent(event: SyncEvent) { events.add(event) }
        override suspend fun updateGroup(group: Group) {}
        override suspend fun updateExpense(expense: Expense) {
            expenses.removeIf { it.id == expense.id }
            expenses.add(expense)
        }
        override suspend fun deleteGroup(group: Group) {}
        override suspend fun insertProfile(profile: Profile) {}
        override suspend fun getSyncEventById(eventId: String): SyncEvent? = events.find { it.eventId == eventId }
        override suspend fun getExpenseById(expenseId: String): Expense? = expenses.find { it.id == expenseId }
        override suspend fun getGroupById(groupId: String): Group? = groups.find { it.id == groupId }
        override suspend fun getUserById(userId: String): User? = users.find { it.id == userId }

        override suspend fun insertTripWithSync(group: Group, users: List<User>, syncEvent: SyncEvent) {
            insertGroup(group)
            users.forEach { insertUser(it) }
            insertSyncEvent(syncEvent)
        }

        override suspend fun insertExpenseWithSync(expense: Expense, syncEvent: SyncEvent) {
            insertExpense(expense)
            insertSyncEvent(syncEvent)
        }

        override suspend fun updateExpenseWithSync(expense: Expense, syncEvent: SyncEvent) {
            updateExpense(expense)
            insertSyncEvent(syncEvent)
        }
    }
}
