package com.example.divide.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.divide.data.ExpenseDatabase
import com.example.divide.data.LocalRepository
import com.example.divide.sync.EventProcessor
import com.example.divide.sync.SyncProtocol
import com.example.divide.sync.SyncSession
import com.example.divide.sync.nearby.NearbyManager
import com.example.divide.sync.protocol.PaymentInfoResponsePayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SyncState { IDLE, ADVERTISING, DISCOVERING, CONNECTING, CONNECTED, ERROR }

sealed class PaymentInfoState {
    object Idle : PaymentInfoState()
    object Requesting : PaymentInfoState()
    data class Success(val payload: PaymentInfoResponsePayload) : PaymentInfoState()
    data class Error(val message: String) : PaymentInfoState()
}

data class DiscoveredUser(val endpointId: String, val endpointName: String)

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val dao = ExpenseDatabase.getDatabase(context).expenseDao()
    private val eventProcessor = EventProcessor(dao)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState

    private val _discoveredUsers = MutableStateFlow<List<DiscoveredUser>>(emptyList())
    val discoveredUsers: StateFlow<List<DiscoveredUser>> = _discoveredUsers

    private val _connectedParticipant = MutableStateFlow<com.example.divide.data.User?>(null)
    val connectedParticipant: StateFlow<com.example.divide.data.User?> = _connectedParticipant

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError

    private val _paymentInfoState = MutableStateFlow<PaymentInfoState>(PaymentInfoState.Idle)
    val paymentInfoState: StateFlow<PaymentInfoState> = _paymentInfoState

    private var nearbyManager: NearbyManager? = null
    private var syncSession: SyncSession? = null
    private var paymentTimeoutJob: Job? = null

    private fun setupSync(groupId: String, isHost: Boolean) {
        val localUserId = LocalRepository.profile.value.userId
        val syncProtocol = SyncProtocol(
            dao = dao,
            eventProcessor = eventProcessor,
            localUserId = localUserId,
            getLocalProfile = { LocalRepository.profile.value }
        )
        val session = SyncSession(groupId, syncProtocol)
        session.onPaymentInfoResponse = { response ->
            paymentTimeoutJob?.cancel()
            _paymentInfoState.value = PaymentInfoState.Success(response)
        }
        session.onParticipantReceived = { user ->
            _connectedParticipant.value = user
        }

        val manager = NearbyManager(
            context = context,
            localEndpointName = LocalRepository.profile.value.name, // Advertise as the user's real profile name
            onMessageReceived = { endpointId, message ->
                session.onMessageReceived(endpointId, message)
            },
            onConnected = { endpointId ->
                _syncError.value = null
                _syncState.value = SyncState.CONNECTED
                session.onConnected(endpointId)
            },
            onDisconnected = { endpointId ->
                session.onDisconnected(endpointId)
                _syncState.value = SyncState.IDLE
            },
            onConnectionFailed = { message ->
                _syncState.value = SyncState.ERROR
                _syncError.value = message
            },
            onEndpointDiscovered = { endpointId, endpointName ->
                val currentList = _discoveredUsers.value
                if (currentList.none { it.endpointId == endpointId }) {
                    _discoveredUsers.value = currentList + DiscoveredUser(endpointId, endpointName)
                }
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
        _connectedParticipant.value = null
        _syncError.value = null
        stopSync()
        setupSync(groupId, isHost = true)
    }

    fun startJoining(groupId: String) {
        _connectedParticipant.value = null
        _syncError.value = null
        stopSync()
        setupSync(groupId, isHost = false)
    }

    fun startSearchingForFriends(groupId: String) {
        _connectedParticipant.value = null
        _syncError.value = null
        stopSync()
        setupSync(groupId, isHost = false)
    }

    fun inviteFriend(endpointId: String) {
        _syncState.value = SyncState.CONNECTING
        _syncError.value = null
        nearbyManager?.requestConnection(endpointId)
    }

    fun requestLivePaymentInfo(targetPayeeUserId: String, amountRupees: Double): Boolean {
        val session = syncSession ?: return false
        paymentTimeoutJob?.cancel()
        _paymentInfoState.value = PaymentInfoState.Requesting
        val sent = session.requestPaymentInfo(targetPayeeUserId, amountRupees)
        if (!sent) {
            _paymentInfoState.value = PaymentInfoState.Error("No active peer connection")
        } else {
            paymentTimeoutJob = viewModelScope.launch {
                delay(5000L) // 5 second timeout for live response
                if (_paymentInfoState.value is PaymentInfoState.Requesting) {
                    _paymentInfoState.value = PaymentInfoState.Error("Payment request timed out")
                }
            }
        }
        return sent
    }

    fun clearPaymentInfoState() {
        paymentTimeoutJob?.cancel()
        _paymentInfoState.value = PaymentInfoState.Idle
    }

    fun stopSync() {
        Log.d("SyncViewModel", "Stopping sync...")
        paymentTimeoutJob?.cancel()
        nearbyManager?.disconnectAll()
        nearbyManager?.stopAdvertising()
        nearbyManager?.stopDiscovery()
        nearbyManager = null
        syncSession = null
        _discoveredUsers.value = emptyList()
        _connectedParticipant.value = null
        _syncError.value = null
        _syncState.value = SyncState.IDLE
        _paymentInfoState.value = PaymentInfoState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopSync()
    }
}
