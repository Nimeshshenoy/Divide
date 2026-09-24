package com.example.divide.sync

import com.example.divide.data.ExpenseDao
import com.example.divide.sync.protocol.*
import com.google.gson.Gson

/**
 * Handles the LOGIC of synchronization.
 *
 * It does NOT know anything about Bluetooth or Nearby Connections.
 *
 * That separation is deliberate.
 */
class SyncProtocol(
    private val dao: ExpenseDao,
    private val eventProcessor: EventProcessor,
    private val localUserId: String
) {

    private val gson = Gson()

    companion object {
        const val PROTOCOL_VERSION = 1
    }

    /**
     * Called immediately after a network connection succeeds.
     */
    fun createHello(
        groupId: String
    ): NetworkMessage {

        val hello = HelloPayload(
            protocolVersion = PROTOCOL_VERSION,
            userId = localUserId,
            groupId = groupId
        )

        return NetworkMessage(
            type = MessageType.HELLO,
            payload = gson.toJson(hello)
        )
    }

    /**
     * Reads our local ledger and tells the peer:
     *
     * "These are the SyncEvents I already know."
     */
    suspend fun createEventIdsMessage(
        groupId: String
    ): NetworkMessage {

        val ids =
            dao.getSyncEventIdsForGroup(groupId)

        val payload = EventIdsPayload(
            groupId = groupId,
            eventIds = ids
        )

        return NetworkMessage(
            type = MessageType.EVENT_IDS,
            payload = gson.toJson(payload)
        )
    }

    /**
     * Compare:
     *
     * events peer knows
     * vs
     * events I know
     *
     * and request only what I'm missing.
     */
    suspend fun createMissingEventRequest(
        peerPayload: EventIdsPayload
    ): NetworkMessage? {

        val myIds =
            dao.getSyncEventIdsForGroup(
                peerPayload.groupId
            ).toSet()

        val missingIds =
            peerPayload.eventIds
                .filterNot { it in myIds }

        // Nothing missing.
        if (missingIds.isEmpty()) {
            return null
        }

        val request =
            RequestEventsPayload(
                groupId = peerPayload.groupId,
                eventIds = missingIds
            )

        return NetworkMessage(
            type = MessageType.REQUEST_EVENTS,
            payload = gson.toJson(request)
        )
    }

    /**
     * Peer says:
     *
     * "Please send event A, B and C."
     *
     * We fetch those events and package them together.
     */
    suspend fun createEventBatch(
        request: RequestEventsPayload
    ): NetworkMessage {

        val events =
            dao.getSyncEventsByIds(
                groupId = request.groupId,
                eventIds = request.eventIds
            )

        val batch =
            EventBatchPayload(
                groupId = request.groupId,
                events = events
            )

        return NetworkMessage(
            type = MessageType.EVENT_BATCH,
            payload = gson.toJson(batch)
        )
    }

    /**
     * Actual incoming historical events finally arrive here.
     *
     * Each one is handed to your existing EventProcessor.
     */
    suspend fun processEventBatch(
        batch: EventBatchPayload
    ) {
        batch.events.forEach { event ->

            // Extra group isolation check.
            if (event.groupId != batch.groupId) {
                return@forEach
            }

            eventProcessor.processIncomingEvent(event)
        }
    }
}
