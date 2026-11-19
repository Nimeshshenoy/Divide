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

    var onPaymentInfoResponse: ((PaymentInfoResponsePayload) -> Unit)? = null

    private val router =
        MessageRouter(
            syncProtocol = syncProtocol,
            expectedGroupId = groupId,
            onPaymentInfoResponseReceived = { response ->
                onPaymentInfoResponse?.invoke(response)
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

    /**
     * Sends an on-demand request for payment details to the connected peer.
     */
    fun requestPaymentInfo(
        targetPayeeUserId: String,
        amountRupees: Double
    ): Boolean {
        val endpointId = lastConnectedEndpointId ?: return false
        val requestMsg = syncProtocol.createPaymentInfoRequest(
            groupId = groupId,
            targetPayeeUserId = targetPayeeUserId,
            amountRupees = amountRupees
        )
        nearbyManager.send(endpointId, requestMsg)
        return true
    }
}
