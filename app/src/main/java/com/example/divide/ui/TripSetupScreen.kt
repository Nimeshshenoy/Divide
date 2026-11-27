package com.example.divide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.divide.data.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Screen used to setup a new trip or edit trip participants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSetupScreen(
    tripId: String? = null,
    syncViewModel: SyncViewModel? = null,
    onSaveAndAddExpense: (String) -> Unit,
    onBack: () -> Unit
) {
    // Observe all trips and current profile from the centralized repository
    val trips by LocalRepository.trips.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    
    // Keep one real trip ID for discovery and the eventual save.
    val draftTripId = remember(tripId) { tripId ?: UUID.randomUUID().toString() }
    val adoptedGroupId by syncViewModel?.adoptedGroupId?.collectAsState() ?: remember { mutableStateOf<String?>(null) }
    val effectiveTripId = adoptedGroupId ?: draftTripId

    val existingTripDetails = remember(tripId, trips) { tripId?.let { id -> trips.find { it.group.id == id } } }

    var tripName by remember(existingTripDetails) { mutableStateOf(existingTripDetails?.group?.name ?: "") }

    // Local state for the temporary users list
    var currentParticipants by remember(existingTripDetails, profile) {
        mutableStateOf(
            existingTripDetails?.users ?: listOf(
                User(
                    id = profile.userId,
                    name = profile.name,
                    groupId = draftTripId,
                    isLocalPlayer = true,
                    upiId = profile.upiId
                )
            )
        )
    }

    var showAddParticipantDialog by remember { mutableStateOf(false) }

    val syncState = syncViewModel?.syncState?.collectAsState()?.value ?: SyncState.IDLE
    val discoveredUsers = syncViewModel?.discoveredUsers?.collectAsState()?.value ?: emptyList()
    val syncError = syncViewModel?.syncError?.collectAsState()?.value
    val connectedParticipant = syncViewModel?.connectedParticipant?.collectAsState()?.value
    val pendingConnection = syncViewModel?.pendingConnection?.collectAsState()?.value

    pendingConnection?.let { pending ->
        AuthPinDialog(
            pendingConnection = pending,
            onAccept = { syncViewModel.acceptPendingConnection() },
            onReject = { syncViewModel.rejectPendingConnection() }
        )
    }

    if (syncState == SyncState.DISCONNECTED) {
        ConnectionLostDialog(
            onReconnect = {
                syncViewModel?.dismissDisconnectedState()
                syncViewModel?.startSearchingForFriends(draftTripId)
                showAddParticipantDialog = true
            },
            onDismiss = {
                syncViewModel?.dismissDisconnectedState()
            }
        )
    }

    // Automatically add newly connected participant
    LaunchedEffect(connectedParticipant) {
        connectedParticipant?.let { user ->
            if (currentParticipants.none { it.id == user.id }) {
                currentParticipants = currentParticipants + user
            }
        }
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tripId == null) "Setup Trip" else "Trip Details") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Field for the Trip/Group name
            TextField(
                value = tripName,
                onValueChange = { tripName = it },
                label = { Text("Trip Name (e.g. Udaipur Trip)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Participants", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${currentParticipants.size} people",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        showAddParticipantDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add people")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(currentParticipants, key = { it.id }) { participant ->
                    ListItem(
                        headlineContent = { Text(participant.name) },
                        supportingContent = {
                            Text(if (participant.isLocalPlayer) "You" else "Trip participant")
                        },
                        trailingContent = {
                            if (!participant.isLocalPlayer) {
                                IconButton(onClick = {
                                    currentParticipants = currentParticipants.filterNot { it.id == participant.id }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove participant")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show existing expenses if viewing an existing trip
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.titleLarge
            )
            
            val activeExpenses = remember(existingTripDetails?.expenses) {
                existingTripDetails?.expenses?.filter { !it.isDeleted } ?: emptyList()
            }
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(activeExpenses) { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = expense.name)
                            Text(text = "₹${expense.amount}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Save Action: Writes to normalized tables and logs SyncEvent
            Button(
                onClick = {
                    if (tripName.isNotBlank() && currentParticipants.isNotEmpty()) {
                        val group = (existingTripDetails?.group ?: Group(id = effectiveTripId, name = tripName))
                            .copy(id = effectiveTripId, name = tripName)
                        val usersToSave = currentParticipants.map { it.copy(groupId = effectiveTripId) }

                        if (tripId == null) {
                            LocalRepository.addTrip(group, usersToSave)
                        } else {
                            LocalRepository.updateTrip(group, usersToSave)
                        }

                        onSaveAndAddExpense(effectiveTripId)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please enter a trip name to save.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Add Expenses")
            }
        }
    }

    // Nearby discovery for adding a participant
    if (showAddParticipantDialog) {
        val permissionLauncher = com.example.divide.sync.nearby.rememberNearbyPermissionsLauncher(
            onPermissionsGranted = {
                syncViewModel?.startSearchingForFriends(effectiveTripId)
            }
        )

        AlertDialog(
            onDismissRequest = {
                if (syncState != SyncState.CONNECTED) {
                    syncViewModel?.stopSync()
                }
                showAddParticipantDialog = false 
            },
            title = { Text("Add Participant") },
            text = {
                Column {
                    Text(
                        "Find and connect to your friend's device to add them to this trip.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    syncError?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    when (syncState) {
                        SyncState.DISCOVERING, SyncState.ADVERTISING -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        if (discoveredUsers.isEmpty()) "Searching nearby devices (0 found)..." else "Found ${discoveredUsers.size} device(s):",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (discoveredUsers.isEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Make sure your friend's phone has Bluetooth and Location turned on, and is also on the Sync or Trip screen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    discoveredUsers.forEach { peer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(peer.endpointName, fontWeight = FontWeight.SemiBold)
                                            Button(onClick = { syncViewModel?.inviteFriend(peer.endpointId) }) {
                                                Text("Connect")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SyncState.CONNECTING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting to device...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        SyncState.CONNECTED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Connected! ${connectedParticipant?.name ?: "Friend"} added to trip.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        SyncState.IDLE, SyncState.ERROR, SyncState.DISCONNECTED -> {
                            OutlinedButton(
                                onClick = { permissionLauncher() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (syncState == SyncState.ERROR) "Retry Nearby Search" else "Find Nearby Devices")
                            }
                        }
                    }

                    if (syncState != SyncState.CONNECTED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                syncViewModel?.simulateTestDeviceConnection(effectiveTripId)
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("🧪 Simulate Test Device Connection")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    if (syncState != SyncState.CONNECTED) {
                        syncViewModel?.stopSync()
                    }
                    showAddParticipantDialog = false 
                }) {
                    Text(if (syncState == SyncState.CONNECTED) "Done" else "Close")
                }
            }
        )
    }
}

/**
 * A square slot that displays a user's name or an Add icon.
 */
@Composable
fun UserSlot(user: User?, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .size(70.dp)
            .border(1.dp, Color.Gray)
            .clickable(enabled = user == null) { onAdd() },
        contentAlignment = Alignment.Center
    ) {
        if (user != null) {
            Text(text = user.name, style = MaterialTheme.typography.bodySmall)
        } else {
            Icon(Icons.Default.Add, contentDescription = "Add User")
        }
    }
}
