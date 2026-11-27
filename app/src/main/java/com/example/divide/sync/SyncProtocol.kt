package com.example.divide.sync

import com.example.divide.data.ExpenseDao
import com.example.divide.data.Profile
import com.example.divide.data.SyncEvent
import com.example.divide.sync.protocol.*
import com.google.gson.Gson
import java.util.UUID

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
    private val localUserId: String,
    private val getLocalProfile: (() -> Profile?)? = null
) {

    private val gson = Gson()

    companion object {
        const val PROTOCOL_VERSION = 1
    }

    fun getSyncEventsFlowForGroup(groupId: String) = dao.getSyncEventsFlowForGroup(groupId)

    suspend fun getKnownEventIds(groupId: String): List<String> = dao.getSyncEventIdsForGroup(groupId)

    fun isLocalUser(authorId: String): Boolean = authorId == localUserId

    fun createParticipantMessage(groupId: String): NetworkMessage {
        return NetworkMessage(
            type = MessageType.PARTICIPANT,
            payload = gson.toJson(
                ParticipantPayload(
                    userId = localUserId,
                    name = getLocalProfile?.invoke()?.name.orEmpty(),
                    upiId = getLocalProfile?.invoke()?.upiId,
                    groupId = groupId
                )
            )
        )
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

    /**
     * Creates an EVENT_ACK message to confirm receipt of specific sync events.
     */
    fun createEventAckMessage(
        groupId: String,
        eventIds: List<String>
    ): NetworkMessage {
        val ackPayload = EventAckPayload(
            groupId = groupId,
            eventIds = eventIds,
            ackingUserId = localUserId
        )
        return NetworkMessage(
            type = MessageType.EVENT_ACK,
            payload = gson.toJson(ackPayload)
        )
    }

    /**
     * Creates an EVENT_BATCH NetworkMessage for a given list of SyncEvents.
     */
    fun createLiveEventBatchMessage(
        groupId: String,
        events: List<SyncEvent>
    ): NetworkMessage {
        val batch = EventBatchPayload(
            groupId = groupId,
            events = events
        )
        return NetworkMessage(
            type = MessageType.EVENT_BATCH,
            payload = gson.toJson(batch)
        )
    }

    suspend fun getAllLocalTripIds(): List<String> = dao.getAllTripIds()

    fun createAllTripIdsMessage(tripIds: List<String>): NetworkMessage {
        val payload = AllTripIdsPayload(
            senderUserId = localUserId,
            tripIds = tripIds
        )
        return NetworkMessage(
            type = MessageType.ALL_TRIP_IDS,
            payload = gson.toJson(payload)
        )
    }

    fun createMissingTripsRequest(missingTripIds: List<String>): NetworkMessage {
        val payload = RequestMissingTripsPayload(
            requestingUserId = localUserId,
            missingTripIds = missingTripIds
        )
        return NetworkMessage(
            type = MessageType.REQUEST_MISSING_TRIPS,
            payload = gson.toJson(payload)
        )
    }

    /**
     * Creates an on-demand message requesting payment details for a specific payee, returning the generated requestId.
     */
    fun createPaymentInfoRequest(
        groupId: String,
        targetPayeeUserId: String,
        amountRupees: Double,
        requestId: String = UUID.randomUUID().toString()
    ): Pair<String, NetworkMessage> {
        val payload = RequestPaymentInfoPayload(
            requestId = requestId,
            groupId = groupId,
            requestingUserId = localUserId,
            targetPayeeUserId = targetPayeeUserId,
            amountRupees = amountRupees
        )
        return requestId to NetworkMessage(
            type = MessageType.REQUEST_PAYMENT_INFO,
            payload = gson.toJson(payload)
        )
    }

    /**
     * Responds to an incoming payment info request with local UPI details.
     */
    suspend fun createPaymentInfoResponse(
        request: RequestPaymentInfoPayload
    ): NetworkMessage? {
        val profile = getLocalProfile?.invoke() ?: return null

        val targetUser = dao.getUserById(request.targetPayeeUserId)
        val localUserInGroup = dao.getLocalUserForGroup(request.groupId)

        val isTargetLocalPlayer = request.targetPayeeUserId == profile.userId ||
                request.targetPayeeUserId == localUserId ||
                (targetUser != null && targetUser.isLocalPlayer) ||
                (localUserInGroup != null && localUserInGroup.id == request.targetPayeeUserId)

        if (!isTargetLocalPlayer) {
            return null
        }

        val response = PaymentInfoResponsePayload(
            requestId = request.requestId,
            groupId = request.groupId,
            payeeUserId = profile.userId,
            payeeName = profile.name,
            upiId = profile.upiId,
            amountRupees = request.amountRupees
        )

        return NetworkMessage(
            type = MessageType.PAYMENT_INFO_RESPONSE,
            payload = gson.toJson(response)
        )
    }

    /**
     * Bundles the ADD_TRIP sync event for the group so connected peers receive trip details immediately.
     */
    suspend fun createAddTripMessage(
        groupId: String
    ): NetworkMessage? {
        val tripDetails = dao.getTripWithDetailsById(groupId) ?: return null
        val addTripEvent = dao.getAddTripSyncEventForGroup(groupId)
            ?: SyncEvent(
                groupId = groupId,
                authorId = localUserId,
                type = "ADD_TRIP",
                payload = gson.toJson(mapOf("group" to tripDetails.group, "users" to tripDetails.users))
            )

        val batch = EventBatchPayload(
            groupId = groupId,
            events = listOf(addTripEvent)
        )

        return NetworkMessage(
            type = MessageType.EVENT_BATCH,
            payload = gson.toJson(batch)
        )
    }
}
