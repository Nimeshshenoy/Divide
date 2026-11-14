package com.example.divide.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.divide.data.ExpenseDatabase
import com.example.divide.sync.EventProcessor
import com.example.divide.sync.SyncProtocol
import com.example.divide.sync.SyncSession
import com.example.divide.sync.nearby.NearbyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SyncState { IDLE, ADVERTISING, DISCOVERING, CONNECTED }

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val dao = ExpenseDatabase.getDatabase(context).expenseDao()
    private val eventProcessor = EventProcessor(dao)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState

    private var nearbyManager: NearbyManager? = null
    private var syncSession: SyncSession? = null

    private fun setupSync(groupId: String, isHost: Boolean) {
        // Using a placeholder for local user ID for now
        val localUserId = "device_local"
        val syncProtocol = SyncProtocol(dao, eventProcessor, localUserId)
        val session = SyncSession(groupId, syncProtocol)
        
        val manager = NearbyManager(
            context = context,
            localEndpointName = Build.MODEL, // Advertise as phone model (e.g. "Pixel 8")
            onMessageReceived = { endpointId, message ->
                session.onMessageReceived(endpointId, message)
            },
            onConnected = { endpointId ->
                _syncState.value = SyncState.CONNECTED
                session.onConnected(endpointId)
            }
        )
        session.attachNearbyManager(manager)
        
        syncSession = session
        nearbyManager = manager

        if (isHost) {
            _syncState.value = SyncState.ADVERTISING
            manager.startAdvertising()
        } else {
            _syncState.value = SyncState.DISCOVERING
            manager.startDiscovery()
        }
    }

    fun startHosting(groupId: String) {
        stopSync()
        setupSync(groupId, isHost = true)
    }

    fun startJoining(groupId: String) {
        stopSync()
        setupSync(groupId, isHost = false)
    }

    fun stopSync() {
        Log.d("SyncViewModel", "Stopping sync...")
        nearbyManager?.disconnectAll()
        nearbyManager?.stopAdvertising()
        nearbyManager?.stopDiscovery()
        nearbyManager = null
        syncSession = null
        _syncState.value = SyncState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        stopSync()
    }
}
