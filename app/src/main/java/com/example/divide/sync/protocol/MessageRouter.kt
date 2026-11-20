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
    private val expectedGroupId: String,
    private val onPaymentInfoResponseReceived: ((PaymentInfoResponsePayload) -> Unit)? = null
) {

    private val gson = Gson()

    suspend fun handle(
        message: NetworkMessage
    ): NetworkMessage? {

        return when (message.type) {

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

                // We are connected to sync one specific group.
                if (
                    expectedGroupId.isNotEmpty() &&
                    expectedGroupId != "pending" &&
                    hello.groupId != expectedGroupId
                ) {
                    return null
                }

                syncProtocol
                    .createEventIdsMessage(
                        expectedGroupId
                    )
            }

            MessageType.EVENT_IDS -> {

                val payload =
                    gson.fromJson(
                        message.payload,
                        EventIdsPayload::class.java
                    )

                if (
                    payload.groupId !=
                    expectedGroupId
                ) {
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

                if (
                    request.groupId !=
                    expectedGroupId
                ) {
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

                if (
                    batch.groupId !=
                    expectedGroupId
                ) {
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

                if (
                    request.groupId !=
                    expectedGroupId
                ) {
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

                if (
                    response.groupId !=
                    expectedGroupId
                ) {
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
