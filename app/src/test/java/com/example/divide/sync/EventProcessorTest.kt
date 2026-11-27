package com.example.divide.sync

import com.example.divide.data.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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

        assertTrue(processor.processIncomingEvent(event))
        assertFalse(processor.processIncomingEvent(event))
    }

    @Test
    fun `test conflict resolution - newer event wins`() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val processor = EventProcessor(fakeDao)
        val gson = Gson()
        val groupId = "group1"
        
        // 1. Process an "Old" event
        val oldExpense = Expense(id = "exp1", groupId = groupId, name = "Old Name", amount = 10.0, payerId = "p1", participantIds = listOf("p1"))
        val oldEvent = SyncEvent(eventId = "ev_old", groupId = groupId, authorId = "a", type = "ADD_EXPENSE", payload = gson.toJson(oldExpense), createdAt = 1000)
        processor.processIncomingEvent(oldEvent)
        
        // 2. Process a "Newer" event for same ID
        val newExpense = Expense(id = "exp1", groupId = groupId, name = "New Name", amount = 20.0, payerId = "p1", participantIds = listOf("p1"))
        val newEvent = SyncEvent(eventId = "ev_new", groupId = groupId, authorId = "a", type = "UPDATE_EXPENSE", payload = gson.toJson(newExpense), createdAt = 2000)
        processor.processIncomingEvent(newEvent)
        
        val finalExpense = fakeDao.expenses.find { it.id == "exp1" }
        assertEquals("New Name", finalExpense?.name)
        assertEquals(20.0, finalExpense?.amount ?: 0.0, 0.01)
    }

    @Test
    fun `test conflict resolution - late arriving old event is ignored`() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val processor = EventProcessor(fakeDao)
        val gson = Gson()
        val groupId = "group1"
        
        // 1. Process "New" event first
        val newExpense = Expense(id = "exp1", groupId = groupId, name = "New Name", amount = 20.0, payerId = "p1", participantIds = listOf("p1"))
        val newEvent = SyncEvent(eventId = "ev_new", groupId = groupId, authorId = "a", type = "ADD_EXPENSE", payload = gson.toJson(newExpense), createdAt = 2000)
        processor.processIncomingEvent(newEvent)
        
        // 2. Process "Old" event that arrived late
        val oldExpense = Expense(id = "exp1", groupId = groupId, name = "Old Name", amount = 10.0, payerId = "p1", participantIds = listOf("p1"))
        val oldEvent = SyncEvent(eventId = "ev_old", groupId = groupId, authorId = "a", type = "UPDATE_EXPENSE", payload = gson.toJson(oldExpense), createdAt = 1000)
        processor.processIncomingEvent(oldEvent)
        
        // Verify state remained "New"
        val finalExpense = fakeDao.expenses.find { it.id == "exp1" }
        assertEquals("New Name", finalExpense?.name)
    }

    @Test
    fun `test tombstone - delete prevents resurrection from late add`() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val processor = EventProcessor(fakeDao)
        val gson = Gson()
        val groupId = "group1"
        val expenseId = "exp_to_die"

        // 1. Process DELETE first (Tombstone created)
        val delEvent = SyncEvent(eventId = "ev_del", groupId = groupId, authorId = "a", type = "DELETE_EXPENSE", payload = gson.toJson(expenseId), createdAt = 2000)
        processor.processIncomingEvent(delEvent)
        
        // 2. Process late ADD
        val lateExpense = Expense(id = expenseId, groupId = groupId, name = "Late Lunch", amount = 50.0, payerId = "p1", participantIds = listOf("p1"))
        val addEvent = SyncEvent(eventId = "ev_add", groupId = groupId, authorId = "a", type = "ADD_EXPENSE", payload = gson.toJson(lateExpense), createdAt = 1000)
        processor.processIncomingEvent(addEvent)
        
        // Item should still be marked as deleted
        val finalExpense = fakeDao.expenses.find { it.id == expenseId }
        assertTrue(finalExpense?.isDeleted ?: false)
    }

    // --- FAKE DAO ---
    
    class FakeExpenseDao : ExpenseDao {
        val events = mutableMapOf<String, SyncEvent>()
        val expenses = mutableListOf<Expense>()
        val groups = mutableListOf<Group>()
        val users = mutableListOf<User>()

        override fun getAllTripsWithDetails(): Flow<List<TripWithDetails>> = emptyFlow()
        override suspend fun getTripWithDetailsById(groupId: String): TripWithDetails? = null
        override fun getProfile(): Flow<Profile?> = emptyFlow()
        override suspend fun insertGroup(group: Group) { groups.add(group) }
        override suspend fun insertUser(user: User) { 
            users.removeIf { it.id == user.id }
            users.add(user) 
        }
        override suspend fun insertExpense(expense: Expense) { 
            expenses.removeIf { it.id == expense.id }
            expenses.add(expense) 
        }
        override suspend fun insertSyncEvent(event: SyncEvent) { events[event.eventId] = event }
        override suspend fun updateGroup(group: Group) {}
        override suspend fun updateExpense(expense: Expense) {
            expenses.removeIf { it.id == expense.id }
            expenses.add(expense)
        }
        override suspend fun deleteGroup(group: Group) {}
        override suspend fun insertProfile(profile: Profile) {}
        override suspend fun getSyncEventById(eventId: String): SyncEvent? = events[eventId]
        override suspend fun getExpenseById(expenseId: String): Expense? = expenses.find { it.id == expenseId }
        override suspend fun getGroupById(groupId: String): Group? = groups.find { it.id == groupId }
        override suspend fun getAllTripIds(): List<String> = groups.map { it.id }
        override suspend fun getUserById(userId: String): User? = users.find { it.id == userId }
        override suspend fun isUserMemberOfGroup(userId: String, groupId: String): Boolean = true
        override suspend fun getSyncEventIdsForGroup(groupId: String): List<String> = events.values.filter { it.groupId == groupId }.map { it.eventId }
        override fun getSyncEventsFlowForGroup(groupId: String): Flow<List<SyncEvent>> = kotlinx.coroutines.flow.flowOf(events.values.filter { it.groupId == groupId })
        override suspend fun getAddTripSyncEventForGroup(groupId: String): SyncEvent? = events.values.firstOrNull { it.groupId == groupId && it.type == "ADD_TRIP" }
        override suspend fun getSyncEventsByIds(groupId: String, eventIds: List<String>): List<SyncEvent> = events.values.filter { it.groupId == groupId && it.eventId in eventIds }
        override suspend fun getLocalUserForGroup(groupId: String): User? = users.find { it.groupId == groupId && it.isLocalPlayer }
        override suspend fun deleteUser(user: User) { users.removeIf { it.id == user.id } }
        override suspend fun deleteSimulatedUsers() {}
        override suspend fun getLegacyLocalUsers(profileUserId: String): List<User> = emptyList()
        override suspend fun getUserByIdAndGroup(userId: String, groupId: String): User? = users.find { it.id == userId && it.groupId == groupId }
        override suspend fun updateUserIdForLocalPlayer(oldUserId: String, groupId: String, newUserId: String) {}

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
        override suspend fun updateUserWithSync(user: User, syncEvent: SyncEvent) {
            insertUser(user)
            insertSyncEvent(syncEvent)
        }
    }
}
