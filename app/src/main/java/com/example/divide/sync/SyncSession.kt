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
    private val sharedTripIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
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
                sharedTripIds.add(adoptedId)
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
            },
            onAllTripIdsReceived = { payload ->
                val localTripIds = syncProtocol.getAllLocalTripIds().toSet()
                val peerTripIds = payload.tripIds.toSet()
                val missingOnLocal = peerTripIds - localTripIds

                sharedTripIds.addAll(localTripIds intersect peerTripIds)
                sharedTripIds.addAll(peerTripIds)

                if (missingOnLocal.isNotEmpty()) {
                    val req = syncProtocol.createMissingTripsRequest(missingOnLocal.toList())
                    lastConnectedEndpointId?.let { ep -> nearbyManager.send(ep, req) }
                }

                // Run historical EVENT_IDS reconciliation for all shared trips
                for (sharedGroupId in sharedTripIds) {
                    if (sharedGroupId.isNotEmpty() && sharedGroupId != "pending") {
                        val eventIdsMsg = syncProtocol.createEventIdsMessage(sharedGroupId)
                        lastConnectedEndpointId?.let { ep -> nearbyManager.send(ep, eventIdsMsg) }
                    }
                }

                lastConnectedEndpointId?.let { ep -> startLiveEventSync(ep) }
                null
            },
            onRequestMissingTripsReceived = { request ->
                for (missingGroupId in request.missingTripIds) {
                    val addTripMsg = syncProtocol.createAddTripMessage(missingGroupId)
                    if (addTripMsg != null) {
                        lastConnectedEndpointId?.let { ep -> nearbyManager.send(ep, addTripMsg) }
                    }
                }
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
            sharedTripIds.add(activeGroupId)
        }

        // 2. Exchange ALL_TRIP_IDS across devices
        sessionScope.launch {
            val allLocalTripIds = syncProtocol.getAllLocalTripIds()
            val allTripIdsMsg = syncProtocol.createAllTripIdsMessage(allLocalTripIds)
            nearbyManager.send(endpointId, allTripIdsMsg)
        }

        // 3. Send HELLO handshake and Participant Payload
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

        // 4. Transmit ADD_TRIP event payload for active group
        if (activeGroupId.isNotEmpty() && activeGroupId != "pending") {
            sessionScope.launch {
                val addTripMsg = syncProtocol.createAddTripMessage(activeGroupId)
                if (addTripMsg != null) {
                    nearbyManager.send(endpointId, addTripMsg)
                }
            }
        }

        // 5. Start live Flow-based event observation & transmission for all shared trips
        startLiveEventSync(endpointId)
    }

    /**
     * Observes the Room database Flow for new SyncEvents in real time across ALL shared trips,
     * transmitting unacknowledged events to the peer with echo prevention and retry fallback.
     */
    private fun startLiveEventSync(endpointId: String) {
        liveSyncJob?.cancel()
        liveSyncJob = sessionScope.launch {
            val allTripIds = (syncProtocol.getAllLocalTripIds() + sharedTripIds + activeGroupId)
                .filter { it.isNotEmpty() && it != "pending" }
                .distinct()

            for (gId in allTripIds) {
                launch {
                    syncProtocol.getSyncEventsFlowForGroup(gId).collect { events ->
                        val unackedEvents = events.filter { event ->
                            event.eventId !in acknowledgedEventIds && syncProtocol.isLocalUser(event.authorId)
                        }

                        if (unackedEvents.isNotEmpty()) {
                            val batchMsg = syncProtocol.createLiveEventBatchMessage(gId, unackedEvents)
                            val sent = nearbyManager.send(endpointId, batchMsg)
                            if (sent) {
                                sessionScope.launch {
                                    delay(3000L)
                                    val stillUnacked = unackedEvents.filter { it.eventId !in acknowledgedEventIds }
                                    if (stillUnacked.isNotEmpty() && lastConnectedEndpointId == endpointId) {
                                        val retryMsg = syncProtocol.createLiveEventBatchMessage(gId, stillUnacked)
                                        nearbyManager.send(endpointId, retryMsg)
                                    }
                                }
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
            sharedTripIds.clear()
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
