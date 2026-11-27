package com.example.divide.sync

import com.example.divide.data.LocalRepository
import com.example.divide.sync.nearby.NearbyManager
import com.example.divide.sync.protocol.MessageRouter
import com.example.divide.sync.protocol.NetworkMessage
import com.example.divide.sync.protocol.PaymentInfoResponsePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Represents ONE active synchronization session for ONE group.
 */
class SyncSession(
    private val groupId: String,
    private val syncProtocol: SyncProtocol
) {

    private lateinit var nearbyManager: NearbyManager
    private var lastConnectedEndpointId: String? = null

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acknowledgedEventIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var liveSyncJob: Job? = null

    var activeGroupId: String = groupId
        private set

    // Pending payment request correlation state
    private var pendingPaymentRequestId: String? = null
    private var pendingTargetPayeeUserId: String? = null

    var onPaymentInfoResponse: ((PaymentInfoResponsePayload) -> Unit)? = null
    var onParticipantReceived: ((com.example.divide.data.User) -> Unit)? = null
    var onGroupIdAdopted: ((String) -> Unit)? = null

    private val router =
        MessageRouter(
            syncProtocol = syncProtocol,
            expectedGroupId = groupId,
            onPaymentInfoResponseReceived = { response ->
                validateAndProcessPaymentResponse(response)
            },
            onParticipantReceived = { participant ->
                onParticipantReceived?.invoke(
                    com.example.divide.data.User(
                        id = participant.userId,
                        name = participant.name,
                        groupId = participant.groupId,
                        isLocalPlayer = false,
                        upiId = participant.upiId
                    )
                )
            },
            onGroupIdAdopted = { adoptedId ->
                activeGroupId = adoptedId
                onGroupIdAdopted?.invoke(adoptedId)
                lastConnectedEndpointId?.let { endpointId ->
                    startLiveEventSync(endpointId)
                }
            },
            onEventAckReceived = { ack ->
                acknowledgedEventIds.addAll(ack.eventIds)
            },
            onEventsReceived = { eventIds ->
                acknowledgedEventIds.addAll(eventIds)
            }
        )

    fun attachNearbyManager(
        manager: NearbyManager
    ) {
        nearbyManager = manager
    }

    /**
     * Called whenever NearbyManager receives something.
     */
    suspend fun onMessageReceived(
        endpointId: String,
        message: NetworkMessage
    ) {
        val reply = router.handle(message)

        if (reply != null) {
            nearbyManager.send(
                endpointId,
                reply
            )
        }
    }

    /**
     * Call this once Nearby reports:
     *
     * STATUS_OK
     */
    fun onConnected(
        endpointId: String
    ) {
        lastConnectedEndpointId = endpointId

        // 1. Ensure local device identity is logged for this group
        if (activeGroupId.isNotEmpty() && activeGroupId != "pending") {
            LocalRepository.joinTrip(activeGroupId)
        }

        // 2. Send HELLO handshake and Participant Payload
        val hello =
            syncProtocol.createHello(
                activeGroupId
            )

        nearbyManager.send(
            endpointId,
            hello
        )

        nearbyManager.send(
            endpointId,
            syncProtocol.createParticipantMessage(activeGroupId)
        )

        // 3. If hosting/connected to a specific group, transmit ADD_TRIP event payload
        if (activeGroupId.isNotEmpty() && activeGroupId != "pending") {
            CoroutineScope(Dispatchers.IO).launch {
                val addTripMsg = syncProtocol.createAddTripMessage(activeGroupId)
                if (addTripMsg != null) {
                    nearbyManager.send(endpointId, addTripMsg)
                }
            }
        }

        // 4. Start live Flow-based event observation & transmission
        startLiveEventSync(endpointId)
    }

    /**
     * Observes the Room database Flow for new SyncEvents in real time, transmitting
     * unacknowledged events to the peer with echo prevention and retry fallback.
     */
    private fun startLiveEventSync(endpointId: String) {
        liveSyncJob?.cancel()
        liveSyncJob = sessionScope.launch {
            val initialKnown = syncProtocol.getKnownEventIds(activeGroupId)
            acknowledgedEventIds.addAll(initialKnown)

            syncProtocol.getSyncEventsFlowForGroup(activeGroupId).collect { events ->
                val unackedEvents = events.filter { event ->
                    event.eventId !in acknowledgedEventIds && syncProtocol.isLocalUser(event.authorId)
                }

                if (unackedEvents.isNotEmpty()) {
                    val batchMsg = syncProtocol.createLiveEventBatchMessage(activeGroupId, unackedEvents)
                    val sent = nearbyManager.send(endpointId, batchMsg)
                    if (sent) {
                        sessionScope.launch {
                            delay(3000L)
                            val stillUnacked = unackedEvents.filter { it.eventId !in acknowledgedEventIds }
                            if (stillUnacked.isNotEmpty() && lastConnectedEndpointId == endpointId) {
                                val retryMsg = syncProtocol.createLiveEventBatchMessage(activeGroupId, stillUnacked)
                                nearbyManager.send(endpointId, retryMsg)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onDisconnected(
        endpointId: String
    ) {
        if (lastConnectedEndpointId == endpointId) {
            lastConnectedEndpointId = null
            liveSyncJob?.cancel()
            liveSyncJob = null
            acknowledgedEventIds.clear()
            clearPendingPaymentRequest()
        }
    }

    /**
     * Sends an on-demand request for payment details to the connected peer and tracks correlation ID.
     */
    fun requestPaymentInfo(
        targetPayeeUserId: String,
        amountRupees: Double
    ): Boolean {
        val endpointId = lastConnectedEndpointId ?: return false
        val (requestId, requestMsg) = syncProtocol.createPaymentInfoRequest(
            groupId = activeGroupId,
            targetPayeeUserId = targetPayeeUserId,
            amountRupees = amountRupees
        )
        val sent = nearbyManager.send(endpointId, requestMsg)
        if (sent) {
            pendingPaymentRequestId = requestId
            pendingTargetPayeeUserId = targetPayeeUserId
        }
        return sent
    }

    fun clearPendingPaymentRequest() {
        pendingPaymentRequestId = null
        pendingTargetPayeeUserId = null
    }

    /**
     * Validates incoming payment info response correlation (requestId and payeeUserId match).
     */
    private fun validateAndProcessPaymentResponse(response: PaymentInfoResponsePayload): Boolean {
        if (pendingPaymentRequestId != null && response.requestId != pendingPaymentRequestId) {
            return false
        }
        if (pendingTargetPayeeUserId != null && response.payeeUserId != pendingTargetPayeeUserId) {
            return false
        }
        onPaymentInfoResponse?.invoke(response)
        clearPendingPaymentRequest()
        return true
    }
}
