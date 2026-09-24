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
    private val expectedGroupId: String
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
                if (hello.groupId != expectedGroupId) {
                    return null
                }

                /*
                 * Later:
                 *
                 * verify cryptographic identity here.
                 *
                 * For now, your EventProcessor membership checks
                 * protect the actual SyncEvents.
                 */

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

            else -> null
        }
    }
}
