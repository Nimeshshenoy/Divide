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
    private val onGroupIdAdopted: ((String) -> Unit)? = null
) {

    private val gson = Gson()
    private var currentGroupId: String = expectedGroupId

    private fun resolveGroupId(incomingGroupId: String): Boolean {
        if (incomingGroupId.isEmpty() || incomingGroupId == "pending") {
            return true
        }
        if (currentGroupId.isEmpty() || currentGroupId == "pending") {
            currentGroupId = incomingGroupId
            onGroupIdAdopted?.invoke(incomingGroupId)
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
                if (resolveGroupId(participant.groupId)) {
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

                // Accept dynamic group ID adoption if in "pending" mode
                if (!resolveGroupId(hello.groupId)) {
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

                if (!resolveGroupId(batch.groupId)) {
                    return null
                }

                syncProtocol
                    .processEventBatch(
                        batch
                    )

                // No immediate reply required.
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
