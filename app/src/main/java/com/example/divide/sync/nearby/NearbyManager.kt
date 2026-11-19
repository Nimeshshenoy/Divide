package com.example.divide.sync.nearby

import android.content.Context
import android.util.Log
import com.example.divide.sync.protocol.MessageCodec
import com.example.divide.sync.protocol.NetworkMessage
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NearbyManager(
    private val context: Context,
    private val localEndpointName: String,
    private val onMessageReceived:
        suspend (endpointId: String, message: NetworkMessage) -> Unit,
    private val onConnected: ((endpointId: String) -> Unit)? = null,
    private val onDisconnected: ((endpointId: String) -> Unit)? = null
) {

    companion object {

        /*
         * Both phones MUST use exactly the same serviceId.
         *
         * Google recommends using your application's package name.
         */
        private const val SERVICE_ID =
            "com.example.divide"

        private val STRATEGY =
            Strategy.P2P_CLUSTER

        private const val TAG =
            "DivideNearby"
    }

    private val connectionsClient =
        Nearby.getConnectionsClient(context)

    private val codec =
        MessageCodec()

    /**
     * Keep track of endpoints that successfully connected.
     */
    private val connectedEndpoints =
        mutableSetOf<String>()

    // =========================================================
    // ADVERTISING
    // =========================================================

    fun startAdvertising() {
        if (!NearbyPermissions.hasRequiredPermissions(context)) {
            Log.e(TAG, "Cannot start advertising: missing required runtime permissions")
            return
        }

        val options =
            AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

        connectionsClient
            .startAdvertising(
                localEndpointName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Advertising started"
                )
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Advertising failed",
                    error
                )
            }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }

    // =========================================================
    // DISCOVERY
    // =========================================================

    fun startDiscovery() {
        if (!NearbyPermissions.hasRequiredPermissions(context)) {
            Log.e(TAG, "Cannot start discovery: missing required runtime permissions")
            return
        }

        val options =
            DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build()

        connectionsClient
            .startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            )
            .addOnSuccessListener {

                Log.d(
                    TAG,
                    "Discovery started"
                )
            }
            .addOnFailureListener { error ->

                Log.e(
                    TAG,
                    "Discovery failed",
                    error
                )
            }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    // =========================================================
    // DISCOVERED DEVICES
    // =========================================================

    private val endpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {

            override fun onEndpointFound(
                endpointId: String,
                info: DiscoveredEndpointInfo
            ) {

                Log.d(
                    TAG,
                    "Found device: ${info.endpointName}"
                )

                /*
                 * SIMPLE MVP:
                 *
                 * Automatically request connection when discovered.
                 *
                 * Later you will probably show the user:
                 *
                 * "Found Nimesh's phone"
                 * [Connect]
                 */

                connectionsClient
                    .requestConnection(
                        localEndpointName,
                        endpointId,
                        connectionLifecycleCallback
                    )
                    .addOnFailureListener { error ->

                        Log.e(
                            TAG,
                            "Connection request failed",
                            error
                        )
                    }
            }

            override fun onEndpointLost(
                endpointId: String
            ) {

                Log.d(
                    TAG,
                    "Endpoint lost: $endpointId"
                )
            }
        }

    // =========================================================
    // CONNECTION LIFECYCLE
    // =========================================================

    private val connectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(
                endpointId: String,
                info: ConnectionInfo
            ) {

                Log.d(
                    TAG,
                    "Connection initiated with ${info.endpointName}"
                )

                /*
                 * DEVELOPMENT ONLY:
                 *
                 * Automatically accept.
                 *
                 * Later, DO NOT blindly auto-accept strangers.
                 *
                 * Nearby gives both phones authentication digits.
                 * Google recommends authenticating the connection.
                 */
                connectionsClient
                    .acceptConnection(
                        endpointId,
                        payloadCallback
                    )
            }

            override fun onConnectionResult(
                endpointId: String,
                result: ConnectionResolution
            ) {

                when (
                    result.status.statusCode
                ) {

                    ConnectionsStatusCodes.STATUS_OK -> {

                        connectedEndpoints +=
                            endpointId

                        Log.d(
                            TAG,
                            "CONNECTED: $endpointId"
                        )

                        onConnected?.invoke(endpointId)
                    }

                    else -> {

                        Log.e(
                            TAG,
                            "Connection failed: " +
                                result.status.statusCode
                        )
                    }
                }
            }

            override fun onDisconnected(
                endpointId: String
            ) {

                connectedEndpoints -=
                    endpointId

                Log.d(
                    TAG,
                    "Disconnected: $endpointId"
                )

                onDisconnected?.invoke(endpointId)
            }
        }

    // =========================================================
    // RECEIVE DATA
    // =========================================================

    private val payloadCallback =
        object : PayloadCallback() {

            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload
            ) {

                if (
                    payload.type !=
                    Payload.Type.BYTES
                ) {
                    return
                }

                val bytes =
                    payload.asBytes()
                        ?: return

                try {

                    val message =
                        codec.decode(bytes)

                    /*
                     * NearbyManager does NOT understand:
                     *
                     * HELLO
                     * EVENT_IDS
                     * expense logic
                     *
                     * It simply forwards the decoded message.
                     */

                    CoroutineScope(
                        Dispatchers.IO
                    ).launch {

                        onMessageReceived(
                            endpointId,
                            message
                        )
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Invalid incoming payload",
                        e
                    )
                }
            }

            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate
            ) {
                /*
                 * BYTES payloads arrive as one complete payload.
                 *
                 * Nothing special required here yet.
                 */
            }
        }

    // =========================================================
    // SEND DATA
    // =========================================================

    fun send(
        endpointId: String,
        message: NetworkMessage
    ): Boolean {

        if (
            endpointId !in connectedEndpoints
        ) {
            return false
        }

        val bytes =
            codec.encode(message)

        val payload =
            Payload.fromBytes(bytes)

        return try {
            connectionsClient
                .sendPayload(
                    endpointId,
                    payload
                )
                .addOnFailureListener { error ->

                    Log.e(
                        TAG,
                        "Send failed",
                        error
                    )
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            false
        }
    }

    fun disconnectAll() {

        connectionsClient.stopAllEndpoints()

        connectedEndpoints.clear()
    }
}
