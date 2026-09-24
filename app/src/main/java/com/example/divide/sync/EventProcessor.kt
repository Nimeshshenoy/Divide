package com.example.divide.sync

import com.example.divide.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles SyncEvents received from other devices.
 *
 * Responsibilities:
 * 1. Ignore duplicate events.
 * 2. Route events to the correct handler.
 * 3. Resolve conflicts deterministically.
 * 4. Preserve ALL known events in sync_events,
 *    even when an event loses a conflict.
 * 5. Apply winning events to the current-state tables.
 *
 * Networking should eventually only deliver SyncEvents here.
 */
class EventProcessor(
    private val dao: ExpenseDao
) {

    private val gson = Gson()

    /**
     * Main entry point for a SyncEvent received from another device.
     *
     * true:
     *      event affected current state
     *
     * false:
     *      duplicate / losing event / unsupported event
     *
     * NOTE:
     * A false result does NOT necessarily mean the event wasn't stored.
     * Losing valid events should still be remembered in sync_events.
     */
    suspend fun processIncomingEvent(
        event: SyncEvent
    ): Boolean = withContext(Dispatchers.IO) {

        // ---------------------------------------------------------
        // STEP 1 — DEDUPLICATION
        // ---------------------------------------------------------
        //
        // If we already know this exact eventId, there is nothing
        // more to do.
        //
        // This gives us idempotency:
        //
        // receiving abc123 once  -> process
        // receiving abc123 again -> ignore
        //
        if (dao.getSyncEventById(event.eventId) != null) {
            return@withContext false
        }

        // ---------------------------------------------------------
        // STEP 2 — AUTHORIZATION (Membership Check)
        // ---------------------------------------------------------
        //
        // Ensure the author of this event is actually a member of
        // the group they are trying to modify.
        //
        if (!isAuthorized(event)) {
            // If they aren't authorized, we still record the event as
            // "seen" so we don't keep asking peers for it, but we
            // never apply its payload to our state.
            recordLosingEvent(event)
            return@withContext false
        }

        // ---------------------------------------------------------
        // STEP 3 — DISPATCH
        // ---------------------------------------------------------
        //
        // Send the event to the handler responsible for its type.
        //
        return@withContext when (event.type) {

            "ADD_TRIP" ->
                handleAddTrip(event)

            "ADD_EXPENSE" ->
                handleAddExpense(event)

            "UPDATE_EXPENSE" ->
                handleUpdateExpense(event)

            "DELETE_EXPENSE" ->
                handleDeleteExpense(event)

            "UPDATE_USER_NAME" ->
                handleUpdateUserName(event)

            else -> {
                // Unknown protocol event.
                //
                // For now we reject it.
                //
                // Later, when protocol versioning is introduced,
                // this can be handled more deliberately.
                false
            }
        }
    }

    /**
     * Determines whether an incoming event should become the
     * current winning state.
     *
     * Rule:
     *
     * 1. Higher createdAt wins.
     * 2. If timestamps are identical, eventId breaks the tie.
     *
     * IMPORTANT:
     *
     * This rule is NOT used to defeat DELETE.
     *
     * DELETE is terminal in Divide.
     */
    private suspend fun isIncomingWinner(
        incoming: SyncEvent,
        currentEventId: String?
    ): Boolean {

        // No event currently owns this state.
        if (currentEventId == null) {
            return true
        }

        val currentEvent =
            dao.getSyncEventById(currentEventId)
                ?: return true

        return if (incoming.createdAt != currentEvent.createdAt) {

            // Later timestamp wins.
            incoming.createdAt > currentEvent.createdAt

        } else {

            // Deterministic tie breaker.
            //
            // Every phone performing this comparison will make
            // the same decision.
            incoming.eventId > currentEvent.eventId
        }
    }

    /**
     * Checks if the author of the event is a member of the group.
     * ADD_TRIP is skipped because the group/membership doesn't exist yet.
     */
    private suspend fun isAuthorized(event: SyncEvent): Boolean {
        if (event.type == "ADD_TRIP") {
            return true
        }

        return dao.isUserMemberOfGroup(
            userId = event.authorId,
            groupId = event.groupId
        )
    }

    /**
     * Stores an event that we know about but which did NOT become
     * the current state.
     *
     * Example:
     *
     * Current expense = ₹1000
     *
     * Old incoming event = ₹900
     *
     * We keep ₹1000 in expenses,
     * but still remember the ₹900 SyncEvent.
     *
     * Otherwise another peer could keep sending us the same event
     * because we would appear not to know it.
     */
    private suspend fun recordLosingEvent(
        event: SyncEvent
    ) {
        dao.insertSyncEvent(event)
    }

    // =============================================================
    // ADD TRIP
    // =============================================================

    private suspend fun handleAddTrip(
        event: SyncEvent
    ): Boolean {

        /*
         * Current ADD_TRIP payload structure:
         *
         * {
         *     "group": {...},
         *     "users": [...]
         * }
         */

        val type =
            object : TypeToken<Map<String, Any>>() {}.type

        val data: Map<String, Any> =
            gson.fromJson(event.payload, type)

        val groupJson =
            gson.toJson(data["group"])

        val incomingGroup =
            gson.fromJson(
                groupJson,
                Group::class.java
            ).copy(
                lastEventId = event.eventId
            )

        val usersJson =
            gson.toJson(data["users"])

        val usersType =
            object : TypeToken<List<User>>() {}.type

        val rawUsers: List<User> =
            gson.fromJson(
                usersJson,
                usersType
            )

        val users =
            rawUsers.map {
                it.copy(
                    lastEventId = event.eventId
                )
            }

        val existingGroup =
            dao.getGroupById(incomingGroup.id)

        // No group yet, or incoming ADD wins.
        if (
            isIncomingWinner(
                event,
                existingGroup?.lastEventId
            )
        ) {

            dao.insertTripWithSync(
                incomingGroup,
                users,
                event
            )

            return true
        }

        // We already know the event now,
        // even though it didn't change state.
        recordLosingEvent(event)

        return false
    }

    // =============================================================
    // ADD EXPENSE
    // =============================================================

    private suspend fun handleAddExpense(
        event: SyncEvent
    ): Boolean {

        val incomingExpense =
            gson.fromJson(
                event.payload,
                Expense::class.java
            ).copy(
                lastEventId = event.eventId
            )

        val existingExpense =
            dao.getExpenseById(
                incomingExpense.id
            )

        // ---------------------------------------------------------
        // RULE: DELETE IS TERMINAL
        // ---------------------------------------------------------
        //
        // Once an expense is deleted, no ADD or UPDATE event may
        // resurrect the same expense ID.
        //
        if (existingExpense?.isDeleted == true) {

            recordLosingEvent(event)

            return false
        }

        // ---------------------------------------------------------
        // NORMAL CONFLICT RESOLUTION
        // ---------------------------------------------------------

        if (
            isIncomingWinner(
                event,
                existingExpense?.lastEventId
            )
        ) {

            dao.insertExpenseWithSync(
                incomingExpense,
                event
            )

            return true
        }

        // Incoming ADD lost, but we still remember it.
        recordLosingEvent(event)

        return false
    }

    // =============================================================
    // UPDATE EXPENSE
    // =============================================================

    private suspend fun handleUpdateExpense(
        event: SyncEvent
    ): Boolean {

        val incomingExpense =
            gson.fromJson(
                event.payload,
                Expense::class.java
            ).copy(
                lastEventId = event.eventId
            )

        val existingExpense =
            dao.getExpenseById(
                incomingExpense.id
            )

        // ---------------------------------------------------------
        // RULE: DELETE IS TERMINAL
        // ---------------------------------------------------------

        if (existingExpense?.isDeleted == true) {

            recordLosingEvent(event)

            return false
        }

        // ---------------------------------------------------------
        // OUT-OF-ORDER DELIVERY
        // ---------------------------------------------------------
        //
        // Example:
        //
        // UPDATE expense123 arrives
        // before
        // ADD expense123
        //
        // Your UPDATE payload contains the entire Expense object,
        // so we already possess enough information to construct
        // the current state.
        //
        if (existingExpense == null) {

            dao.insertExpenseWithSync(
                incomingExpense,
                event
            )

            return true
        }

        // ---------------------------------------------------------
        // NORMAL UPDATE-vs-UPDATE CONFLICT
        // ---------------------------------------------------------

        if (
            isIncomingWinner(
                event,
                existingExpense.lastEventId
            )
        ) {

            dao.updateExpenseWithSync(
                incomingExpense,
                event
            )

            return true
        }

        // Incoming UPDATE lost.
        //
        // Do not change the current Expense,
        // but remember the event.
        recordLosingEvent(event)

        return false
    }

    // =============================================================
    // DELETE EXPENSE
    // =============================================================

    private suspend fun handleDeleteExpense(
        event: SyncEvent
    ): Boolean {

        val expenseId =
            gson.fromJson(
                event.payload,
                String::class.java
            )

        val existingExpense =
            dao.getExpenseById(expenseId)

        // ---------------------------------------------------------
        // ALREADY DELETED
        // ---------------------------------------------------------
        //
        // The current tombstone stays forever.
        //
        // But this is still a different valid DELETE event,
        // so remember it.
        //
        if (existingExpense?.isDeleted == true) {

            recordLosingEvent(event)

            return false
        }

        // ---------------------------------------------------------
        // DELETE EXISTING EXPENSE
        // ---------------------------------------------------------
        //
        // IMPORTANT:
        //
        // We intentionally DO NOT call isIncomingWinner() here.
        //
        // Divide's conflict rule is:
        //
        // DELETE ALWAYS WINS.
        //
        // Therefore even an apparently "older" DELETE beats an
        // UPDATE or ADD for the same Expense ID.
        //
        if (existingExpense != null) {

            val deletedExpense =
                existingExpense.copy(
                    isDeleted = true,
                    lastEventId = event.eventId
                )

            dao.updateExpenseWithSync(
                deletedExpense,
                event
            )

            return true
        }

        // ---------------------------------------------------------
        // DELETE ARRIVED BEFORE ADD
        // ---------------------------------------------------------
        //
        // This is why tombstones are useful.
        //
        // We haven't seen the actual expense yet, but we know:
        //
        // expenseId = X
        // was deleted.
        //
        // So create a minimal placeholder representing that fact.
        //
        // If an old ADD arrives tomorrow,
        // handleAddExpense() sees:
        //
        // isDeleted == true
        //
        // and refuses to resurrect it.
        //
        val tombstone =
            Expense(
                id = expenseId,
                groupId = event.groupId,

                // Placeholder values.
                // UI never displays deleted expenses.
                name = "Unknown (Deleted)",
                amount = 0.0,
                payerId = "unknown",
                participantIds = emptyList(),

                isDeleted = true,
                lastEventId = event.eventId
            )

        dao.insertExpenseWithSync(
            tombstone,
            event
        )

        return true
    }

    // =============================================================
    // UPDATE USER NAME
    // =============================================================

    private suspend fun handleUpdateUserName(
        event: SyncEvent
    ): Boolean {

        val incomingUser =
            gson.fromJson(
                event.payload,
                User::class.java
            ).copy(
                lastEventId = event.eventId
            )

        val existingUser =
            dao.getUserById(
                incomingUser.id
            )

        if (
            isIncomingWinner(
                event,
                existingUser?.lastEventId
            )
        ) {

            /*
             * IMPORTANT:
             *
             * updateUserWithSync() should itself be a Room
             * @Transaction containing:
             *
             * 1. insert/update User
             * 2. insert SyncEvent
             */
            dao.updateUserWithSync(
                incomingUser,
                event
            )

            return true
        }

        // Losing rename is still part of history.
        recordLosingEvent(event)

        return false
    }
}