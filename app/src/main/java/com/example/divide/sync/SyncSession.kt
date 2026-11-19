package com.example.divide.sync

import com.example.divide.sync.nearby.NearbyManager
import com.example.divide.sync.protocol.MessageRouter
import com.example.divide.sync.protocol.NetworkMessage
import com.example.divide.sync.protocol.PaymentInfoResponsePayload

/**
 * Represents ONE active synchronization session for ONE group.
 */
class SyncSession(
    private val groupId: String,
    private val syncProtocol: SyncProtocol
) {

    private lateinit var nearbyManager: NearbyManager
    private var lastConnectedEndpointId: String? = null

    // Pending payment request correlation state
    private var pendingPaymentRequestId: String? = null
    private var pendingTargetPayeeUserId: String? = null

    var onPaymentInfoResponse: ((PaymentInfoResponsePayload) -> Unit)? = null

    private val router =
        MessageRouter(
            syncProtocol = syncProtocol,
            expectedGroupId = groupId,
            onPaymentInfoResponseReceived = { response ->
                validateAndProcessPaymentResponse(response)
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

        val hello =
            syncProtocol.createHello(
                groupId
            )

        nearbyManager.send(
            endpointId,
            hello
        )
    }

    fun onDisconnected(
        endpointId: String
    ) {
        if (lastConnectedEndpointId == endpointId) {
            lastConnectedEndpointId = null
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
            groupId = groupId,
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
