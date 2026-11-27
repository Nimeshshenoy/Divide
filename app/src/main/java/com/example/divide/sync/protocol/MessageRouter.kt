package com.example.divide.sync.protocol

import com.example.divide.sync.SyncProtocol
import com.google.gson.Gson

/**
 * Receives decoded NetworkMessages and decides what protocol action
 * should happen next.
 *
 * It does NOT send anything itself.
 *
 * Instead it returns a NetworkMessage when a reply is required.
 */
class MessageRouter(
    private val syncProtocol: SyncProtocol,
    expectedGroupId: String,
    private val onPaymentInfoResponseReceived: ((PaymentInfoResponsePayload) -> Unit)? = null,
    private val onParticipantReceived: ((ParticipantPayload) -> Unit)? = null,
    private val onGroupIdAdopted: ((String) -> Unit)? = null,
    private val onEventAckReceived: ((EventAckPayload) -> Unit)? = null,
    private val onEventsReceived: ((List<String>) -> Unit)? = null
) {

    private val gson = Gson()
    private var currentGroupId: String = expectedGroupId

    private fun resolveGroupId(incomingGroupId: String, allowAdopt: Boolean = false): Boolean {
        if (incomingGroupId.isEmpty() || incomingGroupId == "pending") {
            return true
        }
        if (currentGroupId.isEmpty() || currentGroupId == "pending" || allowAdopt) {
            if (currentGroupId != incomingGroupId) {
                currentGroupId = incomingGroupId
                onGroupIdAdopted?.invoke(incomingGroupId)
            }
            return true
        }
        return currentGroupId == incomingGroupId
    }

    suspend fun handle(
        message: NetworkMessage
    ): NetworkMessage? {

        return when (message.type) {

            MessageType.PARTICIPANT -> {
                val participant = gson.fromJson(message.payload, ParticipantPayload::class.java)
                if (resolveGroupId(participant.groupId, allowAdopt = true)) {
                    val targetGroupId = if (currentGroupId.isNotEmpty() && currentGroupId != "pending") {
                        currentGroupId
                    } else {
                        participant.groupId
                    }
                    onParticipantReceived?.invoke(participant.copy(groupId = targetGroupId))
                }
                null
            }

            MessageType.HELLO -> {

                val hello =
                    gson.fromJson(
                        message.payload,
                        HelloPayload::class.java
                    )

                // Protocol mismatch.
                if (
                    hello.protocolVersion !=
                    SyncProtocol.PROTOCOL_VERSION
                ) {
                    return null
                }

                // Accept dynamic group ID adoption
                if (!resolveGroupId(hello.groupId, allowAdopt = true)) {
                    return null
                }

                syncProtocol
                    .createEventIdsMessage(
                        currentGroupId
                    )
            }

            MessageType.EVENT_IDS -> {

                val payload =
                    gson.fromJson(
                        message.payload,
                        EventIdsPayload::class.java
                    )

                if (!resolveGroupId(payload.groupId)) {
                    return null
                }

                syncProtocol
                    .createMissingEventRequest(
                        payload
                    )
            }

            MessageType.REQUEST_EVENTS -> {

                val request =
                    gson.fromJson(
                        message.payload,
                        RequestEventsPayload::class.java
                    )

                if (!resolveGroupId(request.groupId)) {
                    return null
                }

                syncProtocol
                    .createEventBatch(
                        request
                    )
            }

            MessageType.EVENT_BATCH -> {

                val batch =
                    gson.fromJson(
                        message.payload,
                        EventBatchPayload::class.java
                    )

                val hasAddTrip = batch.events.any { it.type == "ADD_TRIP" }
                if (!resolveGroupId(batch.groupId, allowAdopt = hasAddTrip)) {
                    return null
                }

                syncProtocol
                    .processEventBatch(
                        batch
                    )

                val eventIds = batch.events.map { it.eventId }
                onEventsReceived?.invoke(eventIds)

                // Return EVENT_ACK to confirm receipt
                if (eventIds.isNotEmpty()) {
                    syncProtocol.createEventAckMessage(currentGroupId, eventIds)
                } else {
                    null
                }
            }

            MessageType.EVENT_ACK -> {

                val ackPayload =
                    gson.fromJson(
                        message.payload,
                        EventAckPayload::class.java
                    )

                if (resolveGroupId(ackPayload.groupId)) {
                    onEventAckReceived?.invoke(ackPayload)
                }

                null
            }

            MessageType.REQUEST_PAYMENT_INFO -> {

                val request =
                    gson.fromJson(
                        message.payload,
                        RequestPaymentInfoPayload::class.java
                    )

                if (!resolveGroupId(request.groupId)) {
                    return null
                }

                syncProtocol
                    .createPaymentInfoResponse(
                        request
                    )
            }

            MessageType.PAYMENT_INFO_RESPONSE -> {

                val response =
                    gson.fromJson(
                        message.payload,
                        PaymentInfoResponsePayload::class.java
                    )

                if (!resolveGroupId(response.groupId)) {
                    return null
                }

                onPaymentInfoResponseReceived?.invoke(response)

                // No immediate network reply required
                null
            }

            else -> null
        }
    }
}
