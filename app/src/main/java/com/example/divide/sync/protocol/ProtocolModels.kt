package com.example.divide.sync.protocol

import com.example.divide.data.SyncEvent
import java.util.UUID

/**
 * Every wireless message gets wrapped in this.
 *
 * IMPORTANT:
 * NetworkMessage != SyncEvent
 *
 * NetworkMessage:
 * "Hey, give me these events"
 *
 * SyncEvent:
 * "Bob added ₹500 dinner"
 */
data class NetworkMessage(
    val type: String,
    val payload: String
)

/**
 * First message exchanged after two phones connect.
 *
 * Think:
 * "Hello, I am this Divide user and I want to sync this group."
 */
data class HelloPayload(
    val protocolVersion: Int = 1,
    val userId: String,
    val groupId: String
)

data class ParticipantPayload(
    val userId: String,
    val name: String,
    val upiId: String?,
    val groupId: String
)

/**
 * A list describing which historical events this phone already knows.
 */
data class EventIdsPayload(
    val groupId: String,
    val eventIds: List<String>
)

/**
 * Requests exact events that this phone is missing.
 */
data class RequestEventsPayload(
    val groupId: String,
    val eventIds: List<String>
)

/**
 * Contains the actual SyncEvents being transferred.
 */
data class EventBatchPayload(
    val groupId: String,
    val events: List<SyncEvent>
)

/**
 * Requests live payment details from a target payee.
 */
data class RequestPaymentInfoPayload(
    val requestId: String = UUID.randomUUID().toString(),
    val groupId: String,
    val requestingUserId: String,
    val targetPayeeUserId: String,
    val amountRupees: Double
)

/**
 * Returns live payment details from the target payee.
 */
data class PaymentInfoResponsePayload(
    val requestId: String,
    val groupId: String,
    val payeeUserId: String,
    val payeeName: String,
    val upiId: String?,
    val amountRupees: Double
)

/**
 * Acknowledges receipt and processing of sync events by a peer.
 */
data class EventAckPayload(
    val groupId: String,
    val eventIds: List<String>,
    val ackingUserId: String
)

object MessageType {
    const val HELLO = "HELLO"
    const val PARTICIPANT = "PARTICIPANT"
    const val EVENT_IDS = "EVENT_IDS"
    const val REQUEST_EVENTS = "REQUEST_EVENTS"
    const val EVENT_BATCH = "EVENT_BATCH"
    const val EVENT_ACK = "EVENT_ACK"
    const val REQUEST_PAYMENT_INFO = "REQUEST_PAYMENT_INFO"
    const val PAYMENT_INFO_RESPONSE = "PAYMENT_INFO_RESPONSE"
}
