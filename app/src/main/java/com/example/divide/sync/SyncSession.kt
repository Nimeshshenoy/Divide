package com.example.divide.sync

import com.example.divide.sync.nearby.NearbyManager
import com.example.divide.sync.protocol.MessageRouter
import com.example.divide.sync.protocol.NetworkMessage

/**
 * Represents ONE active synchronization session for ONE group.
 */
class SyncSession(
    private val groupId: String,
    private val syncProtocol: SyncProtocol
) {

    private lateinit var nearbyManager:
        NearbyManager

    private val router =
        MessageRouter(
            syncProtocol = syncProtocol,
            expectedGroupId = groupId
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
        message:
            NetworkMessage
    ) {

        val reply =
            router.handle(message)

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

        val hello =
            syncProtocol.createHello(
                groupId
            )

        nearbyManager.send(
            endpointId,
            hello
        )
    }
}
